package example.scamp;

import example.scamp.simple.ScampMessage;
import peersim.cdsim.CDProtocol;
import peersim.cdsim.CDState;
import peersim.config.Configuration;
import peersim.core.Linkable;
import peersim.core.Node;
import peersim.edsim.EDProtocol;
import peersim.transport.Transport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by julian on 31/01/15.
 */
public abstract class ScampProtocol implements Linkable, EDProtocol, CDProtocol, example.PeerSamplingService {

    // =================== static fields ==================================
    // ====================================================================


    /**
     * Parameter "c" of Scamp . Defaults to 0.
     *
     * @config
     */
    private static final String PAR_C = "c";

    private static final String SCAMP_PROT = "0";

    /**
     * Time-to-live for indirection. Defaults to -1.
     *
     * @config
     */
    private static final String PAR_INDIRTTL = "indirectionTTL";

    /**
     * Lease timeout. If negative, there is no lease mechanism. Defaults to -1.
     *
     * @config
     */
    private static final String PAR_LEASE_MAX = "leaseTimeoutMax";

    private static final String PAR_LEASE_MIN = "leaseTimeoutMin";

    private static final String PAR_TRANSPORT = "transport";

    /**
     * c
     */
    protected static int c;

    /**
     * indirection TTL
     */
    protected static int indirTTL;

    /**
     * lease timeout
     */
    protected static int leaseTimeoutMin;
    protected static int leaseTimeoutMax;

    protected final int tid;

    protected final int pid;

    /**
     *
     */
    protected int age;

    //protected Map<Long, Node> inView;
    //protected Map<Long, Node> outView;

    protected View inView;
    protected View partialView;

    private int randomLeaseTimeout;

    //private List<Node> outViewList;
    //private List<Node> inViewList;


    public ScampProtocol(String n) {
        ScampProtocol.c = Configuration.getInt(n + "." + PAR_C, 0);
        ScampProtocol.indirTTL = Configuration.getInt(n + "." + PAR_INDIRTTL, -1);
        ScampProtocol.leaseTimeoutMax = Configuration.getInt(n + "." + PAR_LEASE_MAX, -1);
        ScampProtocol.leaseTimeoutMin = Configuration.getInt(n + "." + PAR_LEASE_MIN, -1);
        this.tid = Configuration.getPid(n + "." + PAR_TRANSPORT);
        this.pid = Configuration.lookupPid(SCAMP_PROT);
        inView = new View();
        partialView = new View();
        age = 0;
        this.randomLeaseTimeout = CDState.r.nextInt(leaseTimeoutMax-leaseTimeoutMin) + leaseTimeoutMin;
        System.out.println("Lease:" + this.randomLeaseTimeout);
    }

    public Object clone() {
        ScampProtocol p = null;
        try {
            p = (ScampProtocol) super.clone();
        } catch (CloneNotSupportedException e) {

        }
        p.partialView = new View();
        p.inView = new View();
        p.randomLeaseTimeout = CDState.r.nextInt(leaseTimeoutMax-leaseTimeoutMin) + leaseTimeoutMin;
        System.out.println("Lease:" + p.randomLeaseTimeout);
        return p;
    }

    /*
     * P U B L I C  I N T E R F A C E
     */

    @Override
    public int degree() {
        return this.partialView.length();
    }

    @Override
    public boolean contains(Node neighbor) {
        return this.partialView.contains(neighbor);
    }

    @Override
    public void pack() {

    }

    @Override
    public void onKill() {

    }

    @Override
    public String debug() {
        return this.toString();
    }

    @Override
    public boolean addNeighbor(Node n) {
        return this.addToOutView(n);
    }

    @Override
    public Node getNeighbor(int i) {
        return this.partialView.list().get(i);
    }

    @Override
    public List<Node> getPeers(){
        return this.partialView.list();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("In: [");
        for (Node n : this.inView.list()) {
            sb.append(" ");
            sb.append(n.getID());
        }
        sb.append("], Out: [");
        for (Node n : this.partialView.list()) {
            sb.append(" ");
            sb.append(n.getID());
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public void nextCycle(Node node, int protocolID) {
        subNextCycle(node, protocolID);
        this.age += 1;
    }

    protected abstract void subNextCycle(Node node, int protocolID);

    /*
     * I N T E R N A L  I N T E R F A C E
     */

    protected boolean isExpired() {
        return (this.age >= randomLeaseTimeout);
    }

    protected boolean addToOutView (Node n) {
        if (this.partialView.contains(n)) {
            return false;
        } else {
            this.partialView.add(n);
            return true;
        }
    }

    protected boolean addToInView(Node n) {
        if (this.inView.contains(n)) {
            return false;
        } else {
            this.inView.add(n);
            return true;
        }
    }

    protected boolean p() {
        return CDState.r.nextDouble() < 1.0 / 1.0 + this.degree();
    }

    protected Node randomOutNode() {
        if (degree() > 0) {
            List<Node> out = this.partialView.list();
            return out.get(CDState.r.nextInt(out.size()));
        }
        return null;
    }

    protected Node randomInNode() {
        if (degree() > 0) {
            List<Node> in = this.inView.list();
            return in.get(CDState.r.nextInt(in.size()));
        }
        return null;
    }

    public List<Node> pred() {
        return this.inView.list();
    }

    public List<Node> succ() {
        return this.partialView.list();
    }


    /**
     * this is the first step to enter a network
     * @param contact
     */
    public void join(Node me, Node contact) {
        //this.birthDate = CDState.getCycle();
        this.age = 0;
        if (contact != null) {

            System.err.println("JOIN " + me.getID() + " to contact " + contact.getID());
            //this.inView.clear();
            //this.outView.clear();
            //this.outView.put(contact.getID(), contact);
            this.addNeighbor(contact);
            ScampMessage message = new ScampMessage(me, ScampMessage.Type.Subscribe, me);
            Transport tr = (Transport) me.getProtocol(tid);
            System.err.println("SEND MSG: " + message);
            tr.send(me, contact, message, pid);
        } else {
            System.err.println("JOIN-ERROR:COULD NOT FIND A CONTACT FOR NODE " + me.getID());
        }
    }



}