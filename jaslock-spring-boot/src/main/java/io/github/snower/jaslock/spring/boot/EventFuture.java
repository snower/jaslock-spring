package io.github.snower.jaslock.spring.boot;

import io.github.snower.jaslock.Event;
import io.github.snower.jaslock.SlockDatabase;
import io.github.snower.jaslock.exceptions.EventWaitTimeoutException;
import io.github.snower.jaslock.exceptions.SlockException;

import java.io.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class EventFuture<T> implements Future<T>, Closeable {
    protected final SlockSerializater serializater;
    protected final Event event;
    protected boolean isSetResulted = false;

    public EventFuture(SlockSerializater serializater, SlockDatabase database, byte[] eventKey) {
        this.serializater = serializater;
        this.event = new Event(database, eventKey, 120, 300, false);
    }

    public EventFuture(SlockSerializater serializater, SlockDatabase database, String eventKey) {
        this.serializater = serializater;
        this.event = new Event(database, eventKey, 120, 300, false);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        try {
            return event.isSet();
        } catch (SlockException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        while (true) {
            try {
                return get(120, TimeUnit.SECONDS);
            } catch (TimeoutException ignored) {}
        }
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        int seconds = (int) unit.toSeconds(timeout);
        try {
            event.wait(seconds);
        } catch (EventWaitTimeoutException e) {
            throw new TimeoutException();
        } catch (SlockException e) {
            throw new ExecutionException(e);
        }
        return getRsult();
    }

    public T get(long timeout, TimeUnit unit, Consumer<EventResult<T>> consumer) throws InterruptedException, ExecutionException, TimeoutException {
        int seconds = (int) unit.toSeconds(timeout);
        try {
            event.wait(seconds, callbackFuture -> {
                if (callbackFuture.getException() != null) {
                    consumer.accept(new EventResult<>(callbackFuture.getException()));
                    return;
                }
                EventResult<T> eventResult;
                try {
                    eventResult = new EventResult<>(getRsult());
                } catch (ExecutionException e) {
                    eventResult = new EventResult<>(e.getCause());
                }
                consumer.accept(eventResult);
            });
        } catch (EventWaitTimeoutException e) {
            throw new TimeoutException();
        } catch (SlockException e) {
            throw new ExecutionException(e);
        }
        return null;
    }

    public T getRsult() throws ExecutionException {
        if (event.getCurrentLockData() == null) return null;
        byte[] lockData = event.getCurrentLockData().getDataAsBytes();
        if (lockData == null) return null;
        try  {
            Object eventResult = serializater.deserialize(lockData, new SlockSerializater.TypeReference<EventResult<T>>() {});
            if (!(eventResult instanceof EventResult)) return null;
            if (((EventResult<?>) eventResult).getException() != null) {
                throw new ExecutionException(((EventResult<?>) eventResult).getException());
            }
            return (T) ((EventResult<?>) eventResult).getResult();
        } catch (IOException e) {
            throw new ExecutionException(e);
        }
    }

    public void setResult() throws SlockException, IOException {
        setResult(300, TimeUnit.SECONDS);
    }

    public void setResult(T result) throws SlockException, IOException {
        setResult(result, 300, TimeUnit.SECONDS);
    }

    public void setResult(T result, long expried, TimeUnit unit) throws IOException, SlockException {
        short seconds = (short) unit.toSeconds(expried);
        event.setExpried(seconds);
        EventResult<T> eventResult = new EventResult<>(result);
        event.set(serializater.serializate(eventResult));
        isSetResulted = true;
    }

    public void setResult(long expried, TimeUnit unit) throws SlockException {
        short seconds = (short) unit.toSeconds(expried);
        event.setExpried(seconds);
        event.set();
        isSetResulted = true;
    }

    public void setException(Throwable exception) throws SlockException, IOException {
        setException(exception, 300, TimeUnit.SECONDS);
    }

    public void setException(Throwable exception, long expried, TimeUnit unit) throws SlockException, IOException {
        short seconds = (short) unit.toSeconds(expried);
        event.setExpried(seconds);
        EventResult<T> eventResult = new EventResult<>(exception);
        event.set(serializater.serializate(eventResult));
        isSetResulted = true;
    }

    @Override
    public void close() throws IOException {
        if (isSetResulted) return;
        try {
            event.clear();
        } catch (SlockException e) {
            throw new IOException(e);
        }
    }

    public static class EventResult<T> implements Serializable {
        private static final long serialVersionUID = 1L;
        private T result;
        private Throwable exception;

        public EventResult(T result) {
            this.result = result;
            this.exception = null;
        }

        public EventResult(Throwable exception) {
            this.result = null;
            this.exception = exception;
        }

        public T getResult() {
            return result;
        }

        public void setResult(T result) {
            this.result = result;
        }

        public Throwable getException() {
            return exception;
        }

        public void setException(Throwable exception) {
            this.exception = exception;
        }
    }
}
