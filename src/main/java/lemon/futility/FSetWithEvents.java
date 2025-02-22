package lemon.futility;

import com.google.errorprone.annotations.CheckReturnValue;
import lemon.engine.event.EventWith;
import lemon.engine.event.Observable;
import lemon.engine.toolbox.Disposable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class FSetWithEvents<T> implements Set<T> {
	private final Set<T> backingSet;
	private final EventWith<T> onAdd;
	private final EventWith<T> onRemove;
	public FSetWithEvents() {
		this(new HashSet<>(), new EventWith<>(), new EventWith<>());
	}

	public FSetWithEvents(Set<T> backingSet, EventWith<T> onAdd, EventWith<T> onRemove) {
		this.backingSet = backingSet;
		this.onAdd = onAdd;
		this.onRemove = onRemove;
	}

	public Set<T> backingSet() {
		return Collections.unmodifiableSet(backingSet);
	}

	public EventWith<T> onAdd() {
		return onAdd;
	}

	public EventWith<T> onRemove() {
		return onRemove;
	}

	@Override
	public boolean add(T item) {
		var result = backingSet.add(item);
		if (result) {
			onAdd.callListeners(item);
		}
		return result;
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean remove(Object item) {
		var result = backingSet.remove(item);
		if (result) {
			onRemove.callListeners((T) item);
		}
		return result;
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		for (var item : c) {
			if (!contains(item)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean addAll(Collection<? extends T> c) {
		boolean result = false;
		for (var item : c) {
			if (add(item)) {
				result = true;
			}
		}
		return result;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		var set = c.stream().filter(this::contains).collect(Collectors.toSet());
		return this.removeIf(item -> !set.contains(item));
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		boolean result = false;
		for (var item : c) {
			if (remove(item)) {
				result = true;
			}
		}
		return result;
	}

	@Override
	public void clear() {
		var copy = Set.copyOf(backingSet);
		backingSet.clear();
		copy.forEach(onRemove::callListeners);
	}

	@Override
	public int size() {
		return backingSet.size();
	}

	@Override
	public boolean isEmpty() {
		return backingSet.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return backingSet.contains(o);
	}

	@Override
	public Iterator<T> iterator() {
		var backingIterator = backingSet.iterator();
		return new Iterator<>() {
			T item = null;
			@Override
			public boolean hasNext() {
				return backingIterator.hasNext();
			}

			@Override
			public T next() {
				item = backingIterator.next();
				return item;
			}

			@Override
			public void remove() {
				backingIterator.remove();
				onRemove.callListeners(item);
			}
		};
	}

	@Override
	public Object[] toArray() {
		return backingSet.toArray();
	}

	@Override
	public <T1> T1[] toArray(T1[] a) {
		return backingSet.toArray(a);
	}

	@CheckReturnValue
	public Disposable onAdd(Consumer<? super T> listener) {
		return onAdd.add(listener);
	}

	@CheckReturnValue
	public Disposable onRemove(Consumer<? super T> listener) {
		return onRemove.add(listener);
	}

	@CheckReturnValue
	public Disposable onAnyChange(Consumer<? super T> listener) {
		return Disposable.of(onAdd(listener), onRemove(listener));
	}

	public <U> Set<U> ofFiltered(Class<U> clazz, Consumer<Disposable> disposables) {
		Set<U> set = new HashSet<>();
		for (var item : backingSet) {
			if (clazz.isInstance(item)) {
				set.add(clazz.cast(item));
			}
		}
		disposables.accept(onAdd.add(item -> {
			if (clazz.isInstance(item)) {
				set.add(clazz.cast(item));
			}
		}));
		disposables.accept(onRemove.add(item -> {
			if (clazz.isInstance(item)) {
				set.remove(clazz.cast(item));
			}
		}));
		return Collections.unmodifiableSet(set);
	}

	public Observable<Boolean> observableContains(T item, Consumer<Disposable> disposables) {
		var observable = new Observable<>(contains(item));
		disposables.accept(onAdd.add(added -> {
			if (item == added) {
				observable.setValue(true);
			}
		}));
		disposables.accept(onRemove.add(added -> {
			if (item == added) {
				observable.setValue(false);
			}
		}));
		return observable;
	}
}
