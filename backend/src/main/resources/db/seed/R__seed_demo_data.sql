-- Demo data, loaded only when FLYWAY_LOCATIONS includes classpath:db/seed (docker compose does).
--
-- Repeatable migration that runs on every startup (run marker: ${flyway:timestamp}), and is idempotent:
--   * users, cost centers and requests are created only once (guarded by the seed admin email);
--   * the budget of the CURRENT month is created whenever it is missing, so the demo works in any month.
--
-- Every user's password is Senha@123 (BCrypt below). Timestamps are spread between the start of the
-- current month (America/Sao_Paulo) and now, so every seeded submission falls in the current competence month.

CREATE OR REPLACE FUNCTION seed_request(p_owner BIGINT, p_cost_center BIGINT, p_at TIMESTAMPTZ) RETURNS BIGINT
    LANGUAGE sql AS
$$
INSERT INTO purchase_request (owner_id, cost_center_id, status, created_at, updated_at)
VALUES (p_owner, p_cost_center, 'DRAFT', p_at, p_at)
RETURNING id;
$$;

-- Creates an editable version with its items (pairs of description, quantity, unit price) and the computed total.
CREATE OR REPLACE FUNCTION seed_version(p_request BIGINT, p_number INTEGER, p_title VARCHAR, p_description VARCHAR,
                                        p_category VARCHAR, p_items JSONB, p_at TIMESTAMPTZ) RETURNS BIGINT
    LANGUAGE plpgsql AS
$$
DECLARE
    v_id   BIGINT;
    v_item JSONB;
    v_pos  INTEGER := 0;
BEGIN
    INSERT INTO request_version (request_id, number, title, description, category, needed_by, total, created_at)
    VALUES (p_request, p_number, p_title, p_description, p_category, (p_at AT TIME ZONE 'America/Sao_Paulo')::date + 30, 0, p_at)
    RETURNING id INTO v_id;
    FOR v_item IN SELECT * FROM jsonb_array_elements(p_items)
        LOOP
            v_pos := v_pos + 1;
            INSERT INTO request_item (version_id, position, description, quantity, unit_price, line_total)
            VALUES (v_id, v_pos, v_item ->> 0, (v_item ->> 1)::INTEGER, (v_item ->> 2)::NUMERIC,
                    (v_item ->> 1)::INTEGER * (v_item ->> 2)::NUMERIC);
        END LOOP;
    UPDATE request_version SET total = (SELECT sum(line_total) FROM request_item WHERE version_id = v_id) WHERE id = v_id;
    RETURN v_id;
END;
$$;

-- Freezes a version (the immutability trigger allows this last update) and creates its pending chain.
CREATE OR REPLACE FUNCTION seed_submit(p_version BIGINT, p_levels VARCHAR[], p_at TIMESTAMPTZ) RETURNS VOID
    LANGUAGE plpgsql AS
$$
BEGIN
    UPDATE request_version
    SET submitted_at     = p_at,
        competence_month = date_trunc('month', p_at AT TIME ZONE 'America/Sao_Paulo')::date
    WHERE id = p_version;
    INSERT INTO approval_step (version_id, step_order, level)
    SELECT p_version, ordinality, level FROM unnest(p_levels) WITH ORDINALITY AS chain(level, ordinality);
END;
$$;

CREATE OR REPLACE FUNCTION seed_decide(p_version BIGINT, p_order INTEGER, p_status VARCHAR, p_by BIGINT, p_at TIMESTAMPTZ,
                                       p_comment VARCHAR) RETURNS VOID
    LANGUAGE sql AS
$$
UPDATE approval_step
SET status = p_status, decided_by = p_by, decided_at = p_at, comment = p_comment
WHERE version_id = p_version AND step_order = p_order;
$$;

CREATE OR REPLACE FUNCTION seed_event(p_request BIGINT, p_version INTEGER, p_step INTEGER, p_actor BIGINT, p_action VARCHAR,
                                      p_from VARCHAR, p_to VARCHAR, p_comment VARCHAR, p_at TIMESTAMPTZ) RETURNS VOID
    LANGUAGE sql AS
$$
INSERT INTO request_event (request_id, version_number, step_order, actor_id, actor_role, action, from_status, to_status, comment, occurred_at)
SELECT p_request, p_version, p_step, p_actor, role, p_action, p_from, p_to, p_comment, p_at
FROM app_user WHERE id = p_actor;
$$;

-- Final state of a request: status, current version, lock version (one per write) and last change.
CREATE OR REPLACE FUNCTION seed_finish(p_request BIGINT, p_status VARCHAR, p_current_version INTEGER, p_at TIMESTAMPTZ) RETURNS VOID
    LANGUAGE sql AS
$$
UPDATE purchase_request
SET status          = p_status,
    current_version = p_current_version,
    lock_version    = (SELECT count(*) - 1 FROM request_event WHERE request_id = p_request),
    updated_at      = p_at
WHERE id = p_request;
$$;

DO
$$
DECLARE
    pwd CONSTANT VARCHAR := '$2a$10$ZGs.ZzIf15F/XFa5tOROFePDDDtbjv0U/YMzV1DXnIH5/diXZHzIS';
    month_start TIMESTAMPTZ := date_trunc('month', now() AT TIME ZONE 'America/Sao_Paulo') AT TIME ZONE 'America/Sao_Paulo';
    base        TIMESTAMPTZ := greatest(month_start, now() - INTERVAL '5 days');
    span        INTERVAL    := now() - greatest(month_start, now() - INTERVAL '5 days');
    cc_ti BIGINT; cc_mkt BIGINT;
    req_ti BIGINT; apr1_ti BIGINT; apr2_ti BIGINT;
    req_mkt BIGINT; apr1_mkt BIGINT; apr2_mkt BIGINT;
    admin1 BIGINT; admin2 BIGINT;
    r BIGINT; v BIGINT; v2 BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM app_user WHERE lower(email) = 'admin@example.com') THEN
        RETURN;
    END IF;

    INSERT INTO cost_center (code, name) VALUES ('TI', 'Tecnologia da Informação') RETURNING id INTO cc_ti;
    INSERT INTO cost_center (code, name) VALUES ('MKT', 'Marketing') RETURNING id INTO cc_mkt;

    INSERT INTO app_user (name, email, password_hash, role, approver_level, cost_center_id) VALUES
        ('Ana Souza', 'solicitante.ti@example.com', pwd, 'SOLICITANTE', NULL, cc_ti) RETURNING id INTO req_ti;
    INSERT INTO app_user (name, email, password_hash, role, approver_level, cost_center_id) VALUES
        ('Bruno Lima', 'aprovador1.ti@example.com', pwd, 'APROVADOR', 'NIVEL_1', cc_ti) RETURNING id INTO apr1_ti;
    INSERT INTO app_user (name, email, password_hash, role, approver_level, cost_center_id) VALUES
        ('Carla Mendes', 'aprovador2.ti@example.com', pwd, 'APROVADOR', 'NIVEL_2', cc_ti) RETURNING id INTO apr2_ti;
    INSERT INTO app_user (name, email, password_hash, role, approver_level, cost_center_id) VALUES
        ('Diego Rocha', 'solicitante.mkt@example.com', pwd, 'SOLICITANTE', NULL, cc_mkt) RETURNING id INTO req_mkt;
    INSERT INTO app_user (name, email, password_hash, role, approver_level, cost_center_id) VALUES
        ('Elisa Prado', 'aprovador1.mkt@example.com', pwd, 'APROVADOR', 'NIVEL_1', cc_mkt) RETURNING id INTO apr1_mkt;
    INSERT INTO app_user (name, email, password_hash, role, approver_level, cost_center_id) VALUES
        ('Fábio Nunes', 'aprovador2.mkt@example.com', pwd, 'APROVADOR', 'NIVEL_2', cc_mkt) RETURNING id INTO apr2_mkt;
    INSERT INTO app_user (name, email, password_hash, role, approver_level, cost_center_id) VALUES
        ('Gabriela Admin', 'admin@example.com', pwd, 'ADMIN', NULL, NULL) RETURNING id INTO admin1;
    INSERT INTO app_user (name, email, password_hash, role, approver_level, cost_center_id) VALUES
        ('Heitor Admin', 'admin2@example.com', pwd, 'ADMIN', NULL, NULL) RETURNING id INTO admin2;

    -- TI 1: draft, only the owner sees it
    r := seed_request(req_ti, cc_ti, base + span * 0.02);
    v := seed_version(r, 1, 'Monitores para o time de dados', 'Dois monitores 27" para a squad de dados.', 'EQUIPMENT',
                      '[["Monitor 27 polegadas 4K", 2, "1890.00"]]', base + span * 0.02);
    PERFORM seed_event(r, 1, NULL, req_ti, 'CREATE', NULL, 'DRAFT', NULL, base + span * 0.02);
    PERFORM seed_finish(r, 'DRAFT', 1, base + span * 0.02);

    -- TI 2: SOFTWARE always ends with ADMIN; waiting for NIVEL_1
    r := seed_request(req_ti, cc_ti, base + span * 0.04);
    v := seed_version(r, 1, 'Licenças de IDE', 'Renovação anual das licenças de IDE do time de back-end.', 'SOFTWARE',
                      '[["Licença anual de IDE", 5, "300.00"]]', base + span * 0.04);
    PERFORM seed_event(r, 1, NULL, req_ti, 'CREATE', NULL, 'DRAFT', NULL, base + span * 0.04);
    PERFORM seed_submit(v, ARRAY ['NIVEL_1', 'NIVEL_2', 'ADMIN'], base + span * 0.05);
    PERFORM seed_event(r, 1, NULL, req_ti, 'SUBMIT', 'DRAFT', 'PENDING_APPROVAL', NULL, base + span * 0.05);
    PERFORM seed_finish(r, 'PENDING_APPROVAL', 1, base + span * 0.05);

    -- TI 3: NIVEL_1 approved, waiting for NIVEL_2
    r := seed_request(req_ti, cc_ti, base + span * 0.08);
    v := seed_version(r, 1, 'Consultoria de segurança', 'Teste de intrusão na aplicação de pagamentos.', 'SERVICES',
                      '[["Pentest da aplicação web", 1, "6500.00"], ["Relatório executivo", 1, "1500.00"]]', base + span * 0.08);
    PERFORM seed_event(r, 1, NULL, req_ti, 'CREATE', NULL, 'DRAFT', NULL, base + span * 0.08);
    PERFORM seed_submit(v, ARRAY ['NIVEL_1', 'NIVEL_2'], base + span * 0.09);
    PERFORM seed_event(r, 1, NULL, req_ti, 'SUBMIT', 'DRAFT', 'PENDING_APPROVAL', NULL, base + span * 0.09);
    PERFORM seed_decide(v, 1, 'APPROVED', apr1_ti, base + span * 0.12, 'Escopo adequado.');
    PERFORM seed_event(r, 1, 1, apr1_ti, 'APPROVE', 'PENDING_APPROVAL', 'PENDING_APPROVAL', 'Escopo adequado.', base + span * 0.12);
    PERFORM seed_finish(r, 'PENDING_APPROVAL', 1, base + span * 0.12);

    -- TI 4: above R$ 10.000,00, NIVEL_1 and NIVEL_2 approved, waiting for ADMIN
    r := seed_request(req_ti, cc_ti, base + span * 0.14);
    v := seed_version(r, 1, 'Servidor de homologação', 'Servidor dedicado para o ambiente de homologação.', 'EQUIPMENT',
                      '[["Servidor rack 2U", 1, "15000.00"]]', base + span * 0.14);
    PERFORM seed_event(r, 1, NULL, req_ti, 'CREATE', NULL, 'DRAFT', NULL, base + span * 0.14);
    PERFORM seed_submit(v, ARRAY ['NIVEL_1', 'NIVEL_2', 'ADMIN'], base + span * 0.15);
    PERFORM seed_event(r, 1, NULL, req_ti, 'SUBMIT', 'DRAFT', 'PENDING_APPROVAL', NULL, base + span * 0.15);
    PERFORM seed_decide(v, 1, 'APPROVED', apr1_ti, base + span * 0.18, NULL);
    PERFORM seed_event(r, 1, 1, apr1_ti, 'APPROVE', 'PENDING_APPROVAL', 'PENDING_APPROVAL', NULL, base + span * 0.18);
    PERFORM seed_decide(v, 2, 'APPROVED', apr2_ti, base + span * 0.20, 'Necessário para o projeto Q1.');
    PERFORM seed_event(r, 1, 2, apr2_ti, 'APPROVE', 'PENDING_APPROVAL', 'PENDING_APPROVAL', 'Necessário para o projeto Q1.', base + span * 0.20);
    PERFORM seed_finish(r, 'PENDING_APPROVAL', 1, base + span * 0.20);

    -- TI 5: approved, consumed R$ 800,00 of the TI budget
    r := seed_request(req_ti, cc_ti, base + span * 0.22);
    v := seed_version(r, 1, 'Cadeiras ergonômicas', 'Reposição de cadeiras da sala de suporte.', 'SUPPLIES',
                      '[["Cadeira ergonômica", 4, "200.00"]]', base + span * 0.22);
    PERFORM seed_event(r, 1, NULL, req_ti, 'CREATE', NULL, 'DRAFT', NULL, base + span * 0.22);
    PERFORM seed_submit(v, ARRAY ['NIVEL_1'], base + span * 0.23);
    PERFORM seed_event(r, 1, NULL, req_ti, 'SUBMIT', 'DRAFT', 'PENDING_APPROVAL', NULL, base + span * 0.23);
    PERFORM seed_decide(v, 1, 'APPROVED', apr1_ti, base + span * 0.26, NULL);
    PERFORM seed_event(r, 1, 1, apr1_ti, 'APPROVE', 'PENDING_APPROVAL', 'APPROVED', NULL, base + span * 0.26);
    PERFORM seed_finish(r, 'APPROVED', 1, base + span * 0.26);

    -- TI 6: rejected by NIVEL_2
    r := seed_request(req_ti, cc_ti, base + span * 0.28);
    v := seed_version(r, 1, 'Viagem para conferência', 'Participação em conferência internacional de arquitetura.', 'TRAVEL',
                      '[["Passagem aérea", 1, "2800.00"], ["Hospedagem (3 diárias)", 3, "250.00"]]', base + span * 0.28);
    PERFORM seed_event(r, 1, NULL, req_ti, 'CREATE', NULL, 'DRAFT', NULL, base + span * 0.28);
    PERFORM seed_submit(v, ARRAY ['NIVEL_1', 'NIVEL_2'], base + span * 0.29);
    PERFORM seed_event(r, 1, NULL, req_ti, 'SUBMIT', 'DRAFT', 'PENDING_APPROVAL', NULL, base + span * 0.29);
    PERFORM seed_decide(v, 1, 'APPROVED', apr1_ti, base + span * 0.31, NULL);
    PERFORM seed_event(r, 1, 1, apr1_ti, 'APPROVE', 'PENDING_APPROVAL', 'PENDING_APPROVAL', NULL, base + span * 0.31);
    PERFORM seed_decide(v, 2, 'REJECTED', apr2_ti, base + span * 0.33, 'Fora da política de viagens deste trimestre.');
    PERFORM seed_event(r, 1, 2, apr2_ti, 'REJECT', 'PENDING_APPROVAL', 'REJECTED', 'Fora da política de viagens deste trimestre.', base + span * 0.33);
    PERFORM seed_finish(r, 'REJECTED', 1, base + span * 0.33);

    -- TI 7: changes requested by NIVEL_1; the owner can edit and resubmit (creates v2)
    r := seed_request(req_ti, cc_ti, base + span * 0.35);
    v := seed_version(r, 1, 'Treinamento em cloud', 'Curso preparatório para certificação.', 'SERVICES',
                      '[["Curso online", 1, "900.00"]]', base + span * 0.35);
    PERFORM seed_event(r, 1, NULL, req_ti, 'CREATE', NULL, 'DRAFT', NULL, base + span * 0.35);
    PERFORM seed_submit(v, ARRAY ['NIVEL_1'], base + span * 0.36);
    PERFORM seed_event(r, 1, NULL, req_ti, 'SUBMIT', 'DRAFT', 'PENDING_APPROVAL', NULL, base + span * 0.36);
    PERFORM seed_decide(v, 1, 'CHANGES_REQUESTED', apr1_ti, base + span * 0.38, 'Informe o fornecedor e a carga horária.');
    PERFORM seed_event(r, 1, 1, apr1_ti, 'REQUEST_CHANGES', 'PENDING_APPROVAL', 'CHANGES_REQUESTED', 'Informe o fornecedor e a carga horária.', base + span * 0.38);
    PERFORM seed_finish(r, 'CHANGES_REQUESTED', 1, base + span * 0.38);

    -- TI 8: cancelled by the owner while pending
    r := seed_request(req_ti, cc_ti, base + span * 0.40);
    v := seed_version(r, 1, 'Material de escritório', 'Compra duplicada, cancelada.', 'SUPPLIES',
                      '[["Resma de papel A4", 10, "15.00"]]', base + span * 0.40);
    PERFORM seed_event(r, 1, NULL, req_ti, 'CREATE', NULL, 'DRAFT', NULL, base + span * 0.40);
    PERFORM seed_submit(v, ARRAY ['NIVEL_1'], base + span * 0.41);
    PERFORM seed_event(r, 1, NULL, req_ti, 'SUBMIT', 'DRAFT', 'PENDING_APPROVAL', NULL, base + span * 0.41);
    PERFORM seed_event(r, 1, NULL, req_ti, 'CANCEL', 'PENDING_APPROVAL', 'CANCELLED', NULL, base + span * 0.42);
    PERFORM seed_finish(r, 'CANCELLED', 1, base + span * 0.42);

    -- TI 9: two versions; v1 returned by NIVEL_2 (immutable, still readable), v2 resubmitted with a fresh chain
    r := seed_request(req_ti, cc_ti, base + span * 0.45);
    v := seed_version(r, 1, 'Notebook para estagiário', 'Notebook para o novo estagiário de front-end.', 'EQUIPMENT',
                      '[["Notebook 16 GB RAM", 1, "5200.00"]]', base + span * 0.45);
    PERFORM seed_event(r, 1, NULL, req_ti, 'CREATE', NULL, 'DRAFT', NULL, base + span * 0.45);
    PERFORM seed_submit(v, ARRAY ['NIVEL_1', 'NIVEL_2'], base + span * 0.46);
    PERFORM seed_event(r, 1, NULL, req_ti, 'SUBMIT', 'DRAFT', 'PENDING_APPROVAL', NULL, base + span * 0.46);
    PERFORM seed_decide(v, 1, 'APPROVED', apr1_ti, base + span * 0.48, NULL);
    PERFORM seed_event(r, 1, 1, apr1_ti, 'APPROVE', 'PENDING_APPROVAL', 'PENDING_APPROVAL', NULL, base + span * 0.48);
    PERFORM seed_decide(v, 2, 'CHANGES_REQUESTED', apr2_ti, base + span * 0.50, 'Use o modelo padrão da empresa.');
    PERFORM seed_event(r, 1, 2, apr2_ti, 'REQUEST_CHANGES', 'PENDING_APPROVAL', 'CHANGES_REQUESTED', 'Use o modelo padrão da empresa.', base + span * 0.50);
    v2 := seed_version(r, 2, 'Notebook para estagiário', 'Notebook modelo padrão para o novo estagiário de front-end.', 'EQUIPMENT',
                       '[["Notebook modelo padrão", 1, "4500.00"]]', base + span * 0.52);
    PERFORM seed_event(r, 2, NULL, req_ti, 'EDIT', 'CHANGES_REQUESTED', 'CHANGES_REQUESTED', NULL, base + span * 0.52);
    PERFORM seed_submit(v2, ARRAY ['NIVEL_1', 'NIVEL_2'], base + span * 0.53);
    PERFORM seed_event(r, 2, NULL, req_ti, 'SUBMIT', 'CHANGES_REQUESTED', 'PENDING_APPROVAL', NULL, base + span * 0.53);
    PERFORM seed_finish(r, 'PENDING_APPROVAL', 2, base + span * 0.53);

    -- TI 10: a SOFTWARE request owned by an ADMIN; only the other ADMIN can decide its last step
    r := seed_request(admin1, cc_ti, base + span * 0.55);
    v := seed_version(r, 1, 'Ferramenta de observabilidade', 'Assinatura anual de APM.', 'SOFTWARE',
                      '[["Assinatura anual de APM", 1, "800.00"]]', base + span * 0.55);
    PERFORM seed_event(r, 1, NULL, admin1, 'CREATE', NULL, 'DRAFT', NULL, base + span * 0.55);
    PERFORM seed_submit(v, ARRAY ['NIVEL_1', 'ADMIN'], base + span * 0.56);
    PERFORM seed_event(r, 1, NULL, admin1, 'SUBMIT', 'DRAFT', 'PENDING_APPROVAL', NULL, base + span * 0.56);
    PERFORM seed_decide(v, 1, 'APPROVED', apr1_ti, base + span * 0.58, NULL);
    PERFORM seed_event(r, 1, 1, apr1_ti, 'APPROVE', 'PENDING_APPROVAL', 'PENDING_APPROVAL', NULL, base + span * 0.58);
    PERFORM seed_finish(r, 'PENDING_APPROVAL', 1, base + span * 0.58);

    -- MKT 1: approved, consumes R$ 4.800,00 of the R$ 5.000,00 budget (almost exhausted)
    r := seed_request(req_mkt, cc_mkt, base + span * 0.60);
    v := seed_version(r, 1, 'Campanha de mídia paga', 'Campanha de lançamento nas redes sociais.', 'SERVICES',
                      '[["Gestão de campanha", 1, "4800.00"]]', base + span * 0.60);
    PERFORM seed_event(r, 1, NULL, req_mkt, 'CREATE', NULL, 'DRAFT', NULL, base + span * 0.60);
    PERFORM seed_submit(v, ARRAY ['NIVEL_1', 'NIVEL_2'], base + span * 0.61);
    PERFORM seed_event(r, 1, NULL, req_mkt, 'SUBMIT', 'DRAFT', 'PENDING_APPROVAL', NULL, base + span * 0.61);
    PERFORM seed_decide(v, 1, 'APPROVED', apr1_mkt, base + span * 0.63, NULL);
    PERFORM seed_event(r, 1, 1, apr1_mkt, 'APPROVE', 'PENDING_APPROVAL', 'PENDING_APPROVAL', NULL, base + span * 0.63);
    PERFORM seed_decide(v, 2, 'APPROVED', apr2_mkt, base + span * 0.65, NULL);
    PERFORM seed_event(r, 1, 2, apr2_mkt, 'APPROVE', 'PENDING_APPROVAL', 'APPROVED', NULL, base + span * 0.65);
    PERFORM seed_finish(r, 'APPROVED', 1, base + span * 0.65);

    -- MKT 2: single NIVEL_1 step; approving it gives 422 BUDGET_EXCEEDED (R$ 900,00 needed, R$ 200,00 available)
    r := seed_request(req_mkt, cc_mkt, base + span * 0.70);
    v := seed_version(r, 1, 'Brindes para evento', 'Brindes para o estande da feira do setor.', 'SUPPLIES',
                      '[["Caneca personalizada", 100, "9.00"]]', base + span * 0.70);
    PERFORM seed_event(r, 1, NULL, req_mkt, 'CREATE', NULL, 'DRAFT', NULL, base + span * 0.70);
    PERFORM seed_submit(v, ARRAY ['NIVEL_1'], base + span * 0.71);
    PERFORM seed_event(r, 1, NULL, req_mkt, 'SUBMIT', 'DRAFT', 'PENDING_APPROVAL', NULL, base + span * 0.71);
    PERFORM seed_finish(r, 'PENDING_APPROVAL', 1, base + span * 0.71);

    -- MKT 3: draft
    r := seed_request(req_mkt, cc_mkt, base + span * 0.75);
    v := seed_version(r, 1, 'Banner para fachada', 'Banner de lona para a fachada do escritório.', 'OTHER',
                      '[["Banner de lona 3x1 m", 1, "350.00"]]', base + span * 0.75);
    PERFORM seed_event(r, 1, NULL, req_mkt, 'CREATE', NULL, 'DRAFT', NULL, base + span * 0.75);
    PERFORM seed_finish(r, 'DRAFT', 1, base + span * 0.75);
END;
$$;

-- Budget of the current month for the seeded cost centers, created whenever it is missing. Consumption matches the
-- requests approved in that month, so the budget check constraint and the approval history always agree.
INSERT INTO monthly_budget (cost_center_id, month, amount, consumed)
SELECT cc.id,
       budget.month,
       CASE cc.code WHEN 'TI' THEN 50000.00 ELSE 5000.00 END,
       coalesce((SELECT sum(rv.total)
                 FROM purchase_request pr
                          JOIN request_version rv ON rv.request_id = pr.id AND rv.number = pr.current_version
                 WHERE pr.cost_center_id = cc.id
                   AND pr.status = 'APPROVED'
                   AND rv.competence_month = budget.month), 0)
FROM cost_center cc
         CROSS JOIN (SELECT date_trunc('month', now() AT TIME ZONE 'America/Sao_Paulo')::date AS month) budget
WHERE cc.code IN ('TI', 'MKT')
ON CONFLICT (cost_center_id, month) DO NOTHING;

DROP FUNCTION seed_request(BIGINT, BIGINT, TIMESTAMPTZ);
DROP FUNCTION seed_version(BIGINT, INTEGER, VARCHAR, VARCHAR, VARCHAR, JSONB, TIMESTAMPTZ);
DROP FUNCTION seed_submit(BIGINT, VARCHAR[], TIMESTAMPTZ);
DROP FUNCTION seed_decide(BIGINT, INTEGER, VARCHAR, BIGINT, TIMESTAMPTZ, VARCHAR);
DROP FUNCTION seed_event(BIGINT, INTEGER, INTEGER, BIGINT, VARCHAR, VARCHAR, VARCHAR, VARCHAR, TIMESTAMPTZ);
DROP FUNCTION seed_finish(BIGINT, VARCHAR, INTEGER, TIMESTAMPTZ);
