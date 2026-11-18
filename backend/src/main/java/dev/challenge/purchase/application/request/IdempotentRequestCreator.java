package dev.challenge.purchase.application.request;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import dev.challenge.purchase.application.error.ConflictException;
import dev.challenge.purchase.domain.user.Actor;
import dev.challenge.purchase.infrastructure.persistence.idempotency.IdempotencyKeyEntity;
import dev.challenge.purchase.infrastructure.persistence.idempotency.IdempotencyKeyRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code POST /requests} with {@code Idempotency-Key} (ADR 006). The key is scoped by user and stored with the hash of
 * the payload and the response, in the same transaction that creates the request:
 * <ul>
 * <li>new key: creates the request and stores the response;</li>
 * <li>same key, same payload: returns the stored response, nothing is created;</li>
 * <li>same key, another payload: 409.</li>
 * </ul>
 * Two concurrent calls with the same new key: the unique index lets one commit; the other rolls back entirely and
 * answers with the response the first one stored.
 */
@Service
public class IdempotentRequestCreator {

	private final RequestService requestService;

	private final IdempotencyKeyRepository keys;

	private final JsonMapper jsonMapper;

	private final TransactionTemplate transaction;

	private final Clock clock;

	public IdempotentRequestCreator(RequestService requestService, IdempotencyKeyRepository keys, JsonMapper jsonMapper, PlatformTransactionManager transactionManager, Clock clock) {
		this.requestService = requestService;
		this.keys = keys;
		this.jsonMapper = jsonMapper;
		this.transaction = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/**
	 * @param idempotencyKey {@code null} creates without idempotency
	 */
	public Result create(Actor actor, RequestCommand command, String idempotencyKey) {
		if (idempotencyKey == null) {
			return new Result(requestService.create(actor, command), false);
		}
		var hash = hash(command);
		try {
			return transaction.execute(status -> replay(actor, idempotencyKey, hash).orElseGet(() -> createAndStore(actor, command, idempotencyKey, hash)));
		} catch (DataIntegrityViolationException e) {
			// a concurrent call with the same key committed first
			return transaction.execute(status -> replay(actor, idempotencyKey, hash).orElseThrow(() -> e));
		}
	}

	private Optional<Result> replay(Actor actor, String key, String hash) {
		return keys.findByUserIdAndIdemKey(actor.userId(), key).map(stored -> {
			if (!stored.matches(hash)) {
				throw new ConflictException("IDEMPOTENCY_KEY_REUSED", "A Idempotency-Key " + key + " já foi usada com outro conteúdo. Use uma nova chave para criar outra solicitação.");
			}
			return new Result(jsonMapper.readValue(stored.getResponseBody(), RequestViews.Detail.class), true);
		});
	}

	private Result createAndStore(Actor actor, RequestCommand command, String key, String hash) {
		var detail = requestService.create(actor, command);
		keys.saveAndFlush(new IdempotencyKeyEntity(actor.userId(), key, hash, HttpStatus.CREATED.value(), jsonMapper.writeValueAsString(detail), clock.instant()));
		return new Result(detail, false);
	}

	/**
	 * SHA-256 of the parsed payload, so formatting, key order and equivalent amounts ({@code 10.5} and {@code "10.50"})
	 * produce the same hash.
	 */
	private String hash(RequestCommand command) {
		try {
			var digest = MessageDigest.getInstance("SHA-256").digest(jsonMapper.writeValueAsString(command).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}

	/**
	 * @param replayed whether the response was stored by an earlier call with the same key
	 */
	public record Result(RequestViews.Detail detail, boolean replayed) {
	}

}
