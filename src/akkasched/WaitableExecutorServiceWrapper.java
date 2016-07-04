package akkasched;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/*
 * Wraps an ExecutorService to provide ability to wait until all pending tasks
 * are finished.
 */

public class WaitableExecutorServiceWrapper implements ExecutorService {
	ExecutorService executor;
	IdentityHashMap<Object, Boolean> running = new IdentityHashMap<>();

	class WrappedTask implements Runnable {
		Runnable task;

		public WrappedTask(Runnable task) {
			this.task = task;
		}

		@Override
		public void run() {
			try {
				synchronized (running) {
					running.put(this, true);
				}
				task.run();
			} finally {
				synchronized (running) {
					running.remove(this);
					running.notifyAll();
				}
			}
		}
	}

	class WrappedCallable<T> implements Callable<T> {

		Callable<T> callable;

		public WrappedCallable(Callable<T> callable) {
			this.callable = callable;
		}

		@Override
		public T call() throws Exception {
			try {
				synchronized (running) {
					running.put(this, true);
				}
				return callable.call();
			} finally {
				synchronized (running) {
					running.remove(this);
					running.notifyAll();
				}
			}
		}
	}

	public WaitableExecutorServiceWrapper(ExecutorService executor) {
		this.executor = executor;
	}
	
	public void awaitTasksExecuted() throws InterruptedException {
		synchronized(running) {
			while (!running.isEmpty())
				running.wait();
		}
	}

	@Override
	public void execute(Runnable task) {
		executor.execute(new WrappedTask(task));
	}

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit)
			throws InterruptedException {
		return executor.awaitTermination(timeout, unit);
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
			throws InterruptedException {
		ArrayList<Callable<T>> wrapped = new ArrayList<>();
		for (Callable<T> task : tasks)
			wrapped.add(new WrappedCallable<>(task));
		return executor.invokeAll(wrapped);
	}

	@Override
	public <T> List<Future<T>> invokeAll(
			Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
			throws InterruptedException {
		ArrayList<Callable<T>> wrapped = new ArrayList<>();
		for (Callable<T> task : tasks)
			wrapped.add(new WrappedCallable<>(task));
		return executor.invokeAll(wrapped, timeout, unit);
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
			throws InterruptedException, ExecutionException {
		ArrayList<Callable<T>> wrapped = new ArrayList<>();
		for (Callable<T> task : tasks)
			wrapped.add(new WrappedCallable<>(task));
		return executor.invokeAny(wrapped);
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks,
			long timeout, TimeUnit unit) throws InterruptedException,
			ExecutionException, TimeoutException {
		ArrayList<Callable<T>> wrapped = new ArrayList<>();
		for (Callable<T> task : tasks)
			wrapped.add(new WrappedCallable<>(task));
		return executor.invokeAny(wrapped, timeout, unit);
	}

	@Override
	public boolean isShutdown() {
		return executor.isShutdown();
	}

	@Override
	public boolean isTerminated() {
		return executor.isTerminated();
	}

	@Override
	public void shutdown() {
		executor.shutdown();
	}

	@Override
	public List<Runnable> shutdownNow() {
		List<Runnable> unexecuted = executor.shutdownNow();
		ArrayList<Runnable> unwrapped = new ArrayList<>();
		for (Runnable runnable : unexecuted) {
			if (runnable instanceof WrappedTask)
				unwrapped.add(((WrappedTask) runnable).task);
			else
				throw new UnsupportedOperationException();
		}
		return unwrapped;
	}

	@Override
	public <T> Future<T> submit(Callable<T> task) {
		return executor.submit(new WrappedCallable<>(task));
	}

	@Override
	public Future<?> submit(Runnable task) {
		return executor.submit(new WrappedTask(task));
	}

	@Override
	public <T> Future<T> submit(Runnable task, T result) {
		return executor.submit(new WrappedTask(task), result);
	}
}
