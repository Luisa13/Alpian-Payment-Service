package ch.example.template.application.tasks;

/**
 * Base contract for all application tasks.
 *
 * <p>A task is a single, atomic, reusable unit of work. Tasks are composed by flows,
 * which orchestrate them in a specific sequence to fulfil one use-case.
 *
 * <p>Rules:
 * <ul>
 *   <li>Keep tasks small: one responsibility, no orchestration of other tasks.
 *   <li>Tasks never own a transaction: do <b>not</b> annotate them with {@code @Transactional}.
 *       The calling flow is the only transaction boundary; tasks join it.
 * </ul>
 * Enforced by {@code ArchitectureTest}.
 *
 * @param <A> argument type (use {@code Void} for no-arg tasks)
 * @param <R> result type (use {@code Void} for side-effect-only tasks)
 */
public interface Task<A, R> {
    R execute(A args);
}
