package example.paper.scamplon;

import example.paper.Dynamic;
import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Node;

import java.util.*;

/**
 * THIS is not EVENT-based due to simplification
 * Created by julian on 3/31/15.
 */
public class FastScamplon  extends example.Scamplon.ScamplonProtocol implements Dynamic, PartialView.Parent {

    private static final String PARAM_START_SHUFFLE = "startShuffle";

    // ============================================
    // E N T I T Y
    // ============================================

    private PartialView partialView;
    private Map<Long, Node> inView;
    private boolean isUp = true;
    private static final int FORWARD_TTL = 25;
    private final int startShuffle;

    public FastScamplon(String prefix) {
        super(prefix);
        this.startShuffle = Configuration.getInt(prefix + "." + PARAM_START_SHUFFLE, 0);
        this.partialView = new PartialView();
        this.inView = new HashMap<Long, Node>();
    }

    @Override
    public Object clone() {
        FastScamplon s = (FastScamplon) super.clone();
        s.partialView = new PartialView();
        s.inView = new HashMap<Long, Node>();
        return s;
    }


    // ============================================
    // P U B L I C
    // ============================================

    @Override
    public void nextCycle(Node node, int protocolID) {
        if (this.isUp()) {
            this.startShuffle(node);
        }
    }

    @Override
    public boolean isUp() {
        return this.isUp;
    }

    @Override
    public void up() {
        this.isUp = true;
    }

    @Override
    public void down() {
        this.isUp = false;
    }

    @Override
    public int hash() {
        return 0;
    }

    @Override
    public void processEvent(Node node, int pid, Object event) {
        // N E V E R  U S E D
    }

    @Override
    public int degree() {
        if (true) return this.partialView.degree();
        int count = 0;
        for (int i = 0; i < this.partialView.degree(); i++) {
            final FastScamplon N = (FastScamplon) this.partialView.get(i).getProtocol(pid);
            if (N.isUp()) {
                count++;
            }
        }
        return count;
        //return this.partialView.degree();
    }

    @Override
    public Node getNeighbor(int i) {
        return this.partialView.get(i);
    }

    @Override
    public boolean addNeighbor(Node neighbour) {
        return this.partialView.add(neighbour);
    }

    @Override
    public boolean contains(Node neighbor) {
        return this.partialView.contains(neighbor);
    }

    @Override
    public List<Node> getPeers() {
        return this.partialView.list();
    }

    @Override
    public String debug() {
        StringBuilder sb = new StringBuilder();
        sb.append(", in:[");
        for (Node n : this.inView.values()) {
            if (sb.length() > 4) {
                sb.append(",");
            }
            sb.append(n.getID());
        }
        sb.append("], out:");
        sb.append(this.partialView);
        sb.append(", isUp:");
        sb.append(this.isUp());
        return sb.toString();
    }

    // ============================================
    // C Y C L O N
    // ============================================

    /**
     *
     *      A* --> B
     *
     * @param me
     */
    public void startShuffle(Node me) {
        this.updateInView(me);
        if (this.isUp() && this.startShuffle < CommonState.getTime()) {
            if (this.degree() > 0) {
                this.partialView.freeze();
                this.partialView.incrementAge();
                final PartialView.Entry q = this.partialView.oldest();
                final List<PartialView.Entry> nodesToSend = this.partialView.subsetMinus1(q);
                nodesToSend.add(new PartialView.Entry(me));
                final FastScamplon Q = (FastScamplon) q.node.getProtocol(pid);
                if (Q.isUp()) {
                    Q.receiveShuffle(q.node, me, PartialView.clone(nodesToSend), this.degree());
                } else {
                    // TIME OUT
                    this.partialView.deleteAll(q.node);
                    this.inView.remove(q.node.getID());
                }
            }
        }
    }

    /**
     *
     *      A --> B*
     *
     * @param me
     * @param sender
     * @param received
     * @param otherPartialViewSize
     */
    public void receiveShuffle(
            final Node me,
            final Node sender,
            final List<PartialView.Entry> received,
            final int otherPartialViewSize) {
        if (this.isUp()) {
            this.partialView.freeze();
            List<PartialView.Entry> nodesToSend = this.partialView.subset();
            final int size = this.degree();
            //.println("receive shuffle @" + me.getID() + " from " + sender.getID());
            //System.err.println("Send nodes: " + nodesToSend);
            this.partialView.merge(me, sender, received, otherPartialViewSize, false);
            this.updateInView(me);
            this.updateOutView(me);
            final FastScamplon P = (FastScamplon) sender.getProtocol(pid);

            P.finishShuffle(
                    sender,
                    me,
                    PartialView.clone(nodesToSend),
                    size);
        }
    }

    /**
     *
     *      A* --> B
     *
     * @param me
     * @param sender
     * @param received (FROM B)
     * @param otherPartialViewSize
     */
    public void finishShuffle(
            final Node me,
            final Node sender,
            final List<PartialView.Entry> received,
            final int otherPartialViewSize) {
        if (this.isUp()) {
            //System.err.println("finish shuffle @" + me.getID() + " from " + sender.getID());
            this.partialView.merge(me, sender, received, otherPartialViewSize);
            this.updateInView(me);
            this.updateOutView(me);
        }
    }

    // ============================================
    // S C A M P
    // ============================================

    /**
     * Transform
     *  a --> (me) --> b
     *  into
     *  a --> b
     *
     * @param node
     */
    public static void unsubscribe(Node node) {
        //if (true) return;
        final FastScamplon current = (FastScamplon) node.getProtocol(pid);
        current.updateInView(node);
        //System.err.println(current.debug());
        if (current.isUp()) {
            int count = 0;
            current.down();
            final int ls = current.inView.size();
            final int notifyIn = Math.max(ls - c - 1, 0);
            final Queue<Node> in = new LinkedList<Node>(current.inView.values());
            final List<Node> out = current.partialView.list();
            for (int i = 0; i < notifyIn && out.size() > 0; i++) {
                final Node a = in.poll();
                final Node b = out.get(i % out.size());
                count += current.replace(node, a, b);
            }

            while (!in.isEmpty()) {
                final FastScamplon next = (FastScamplon) in.poll().getProtocol(pid);
                count += next.partialView.deleteAll(node);
            }
            System.err.println("remove " + node.getID() + ", delete " + count + " arcs");
            current.partialView.clear();
            current.inView.clear();
        }
    }

    /**
     * SUBSCRIBE
     * @param s
     * @param c
     */
    public static void subscribe(final Node s, final Node c) {
        final FastScamplon subscriber = (FastScamplon) s.getProtocol(pid);
        subscriber.inView.clear();
        subscriber.partialView.clear();
        final FastScamplon contact = (FastScamplon) c.getProtocol(pid);
        int count = 0;
        //System.err.println("subscribe " + s.getID() + " to " + c.getID() + " with " + contact.partialView);
        List<Long> ids = new ArrayList<Long>();
        if (subscriber.isUp() && contact.isUp()) {
            subscriber.addNeighbor(c);
            for (Node n : contact.getPeers()) {
                ids.add(n.getID());
                count += forward(s, n, 0) ? 1 : 0;
            }
            //System.err.println(" c is " + FastScamplon.c);
            for (int i = 0; i < FastScamplon.c && contact.degree() > 0; i++) {
                Node n = contact.getNeighbor(CommonState.r.nextInt(contact.degree()));
                ids.add(n.getID());
                count += forward(s, n, 0) ? 1 : 0;
            }
            //System.err.println("subscribed " + s.getID() + " to " + count + " nodes: " + ids);
        } else {
            throw new RuntimeException("@Subscribe (" + s.getID() + " -> " + c.getID() + " not up");
        }
        //System.err.println("add " + s.getID() + ", add " + count + " arcs");
    }

    /**
     * FORWARD
     * @param s
     * @param node
     * @param counter
     */
    public static boolean forward(final Node s, final Node node, int counter) {
        final FastScamplon N = (FastScamplon) node.getProtocol(pid);
        if (N.isUp()) {
            counter++;
            if (counter < FORWARD_TTL) {
                final FastScamplon current = (FastScamplon) node.getProtocol(pid);
                if (current.partialView.p() && !current.contains(s) && node.getID() != s.getID()) {
                    final FastScamplon subscriber = (FastScamplon) s.getProtocol(pid);
                    current.addNeighbor(s);
                    subscriber.addToInview(s, node);
                    return true;
                } else if (current.degree() > 0) {
                    Node next = current.partialView.get(CommonState.r.nextInt(current.degree()));
                    return forward(s, next, counter);
                } else {
                    System.err.println("DEAD END for subscription " + s.getID() + " @" + node.getID());
                    return false;
                }
            } else {
                //System.err.println("Forward for " + s.getID() + " timed out @" + node.getID());
                return false;
            }
        }
        return false;
    }

    // =================================================================
    // H E L P E R
    // =================================================================

    private void updateInView(Node me) {
        List<Node> in = new ArrayList<Node>(this.inView.values());
        for (Node n : in) {
            final FastScamplon current = (FastScamplon) n.getProtocol(pid);
            if (!current.isUp() || !current.contains(me)) {
                this.inView.remove(n.getID());
            }
        }
    }

    private void updateOutView(Node me) {
        for (Node n : this.getPeers()) {
            final FastScamplon current = (FastScamplon) n.getProtocol(pid);
            if (!current.inView.containsKey(me.getID())) {

                current.addToInview(n, me);
            }
        }
    }


    /**
     * Turn
     *      a --> (me) --> b
     * Into
     *      a --> b
     *
     * @param me
     * @param a
     * @param b
     * @return
     */
    private int replace(Node me, Node a, Node b) {
        int count = this.partialView.count(b); // not really accurate
        final FastScamplon A = (FastScamplon) a.getProtocol(pid);
        final FastScamplon B = (FastScamplon) b.getProtocol(pid);
        if (me.getID() == a.getID() || me.getID() == b.getID()) {
            throw new RuntimeException("FATAL");
        }
        if (a.isUp() && b.isUp()) {
            if (a.getID() == b.getID()) {
                count += A.partialView.deleteAll(me);
                B.inView.remove(me.getID());
            } else {
                final int swn = A.partialView.switchNode(me, b);
                count += Math.max(swn/2, 1); // inaccurate
                B.inView.remove(me.getID());
                if (!B.inView.containsKey(a.getID())) {
                    B.addToInview(b, a);
                }
            }
        } else {
            // either a or b is down, so we just kill all links regarding (me)
            count += A.partialView.deleteAll(me);
            B.inView.remove(me.getID());
        }
        return count;
    }

    private void addToInview(Node me, Node n) {
        if (me.getID() == n.getID()) {
            throw new RuntimeException("cannot put myself");
        }
        if (!this.inView.containsKey(n.getID())) {
            this.inView.put(n.getID(), n);
        }
    }

}