package jsettlers.algorithms.simplebehaviortree;

import java.util.List;

import java8.util.Lists2;

public abstract class Composite<T> extends Node<T> {
	private static final long serialVersionUID = 8795400757387672902L;

	protected final List<Node<T>> children;

	protected Composite(Node<T>[] children) {
		this.children = Lists2.of(children);
	}

	@Override
	int initiate(int maxId) {
		maxId = super.initiate(maxId);

		for (Node<T> child : children) {
			maxId = child.initiate(maxId);
		}

		return maxId;
	}
}