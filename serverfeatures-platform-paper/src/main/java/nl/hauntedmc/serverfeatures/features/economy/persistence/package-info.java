/**
 * Transactional persistence for Economy.
 *
 * <p>{@code EconomyRepository} is the caller-facing façade. Session-level stores own account
 * provisioning, mutations, payment policy, idempotency, ledger writes, definition validation,
 * and queries. Mutation collaborators always share a single ORM transaction.</p>
 */
package nl.hauntedmc.serverfeatures.features.economy.persistence;
