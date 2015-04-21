package descent.scamp;

import java.util.ArrayList;
import java.util.List;

import peersim.core.CommonState;
import peersim.core.Node;

/**
 * Created by julian on 3/16/15.
 */
public class View {

	static long leaseTimeoutMin;
	static long leaseTimeoutMax;

	// =====================================
	// P R O P E R T I E S
	// =====================================

	List<ViewEntry> array = new ArrayList<ViewEntry>();

	public View(int minLease, int maxLease) {
		leaseTimeoutMax = maxLease;
		leaseTimeoutMin = minLease;
	}

	// =====================================
	// P U B L I C
	// =====================================

	public void clear() {
		this.array.clear();
	}

	/**
	 * only call this on the outview!
	 */
	public void updateTimeouts() {
		final List<Node> delete = new ArrayList<Node>();
		for (ViewEntry e : this.array) {
			if (e.timeout()) {
				delete.add(e.node);
			}
		}
		for (Node e : delete) {
			if (!this.delete(e)) {
				throw new RuntimeException("element must be in list");
			}
		}
	}

	public int size() {
		return this.array.size();
	}

	/**
	 * @return
	 */
	public Node getRandom() {
		if (this.array.size() > 0) {
			final int pos = CommonState.r.nextInt(this.array.size());
			return this.array.get(pos).node;
		}
		return null;
	}

	/**
	 * @param n
	 * @return
	 */
	public boolean delete(Node n) {
		int i = this.indexOf(n);
		if (i >= 0) {
			this.array.remove(i);
			return true;
		}
		return false;
	}

	/**
	 * This must only be used for the inviews as, there, we dont care about when
	 * the node expires!
	 * 
	 * @param n
	 * @return
	 */
	public ViewEntry add(Node n) {
		if (!this.contains(n)) {
			ViewEntry entry = new ViewEntry(n);
			this.array.add(entry);
			return entry;
		}
		return null;
	}

	/**
	 * @param e
	 * @return
	 */
	public boolean add(ViewEntry e) {
		if (!this.contains(e.node)) {
			this.array.add(e);
			return true;
		}
		return false;
	}

	/**
	 * @return
	 */
	public List<Node> list() {
		final List<Node> result = new ArrayList<Node>();
		for (ViewEntry e : this.array) {
			result.add(e.node);
		}
		return result;
	}

	/**
	 * @param n
	 * @return
	 */
	public boolean contains(Node n) {
		return this.indexOf(n) >= 0;
	}

	public boolean contains(ViewEntry ve) {
		final int i = indexOf(ve.node);
		if (i >= 0) {
			final ViewEntry my = this.array.get(i);
			if (my.birthdate < ve.birthdate) {
				this.delete(my.node);
				return false;
			} else {
				return true;
			}
		}
		return false;
	}

	/**
	 * @param n
	 * @return
	 */
	public int indexOf(Node n) {
		for (int i = 0; i < this.array.size(); i++) {
			if (this.array.get(i).node.getID() == n.getID()) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append("[");
		for (ViewEntry e : this.array) {
			if (sb.length() > 1) {
				sb.append(",");
			}
			sb.append(e);
		}
		return sb.toString();
	}

	// =====================================
	// H E L P E R C L A S S E S
	// =====================================

	public static final ViewEntry generate(Node me) {
		return new ViewEntry(me);
	}

}
