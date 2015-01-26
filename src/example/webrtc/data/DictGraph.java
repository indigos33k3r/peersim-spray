package example.webrtc.data;

import example.webrtc.PeerSamplingService;
import example.webrtc.cyclon2.Cyclon;
import peersim.core.Node;

import java.util.*;

/**
 * Created by julian on 26/01/15.
 */
public class DictGraph {

    /* =================================================================== *
     * SINGLETON
     * =================================================================== */

    private static DictGraph singleton;

    public static DictGraph getSingleton(int size) {
        if (singleton == null) {
            singleton = new DictGraph(size);
        }
        return singleton;
    }

    /* =================================================================== *
     * PROPERTIES
     * =================================================================== */

    //public final GraphNode[] nodes;
    public final Map<Long, DictNode> nodes;

    private List<DictNode> neighbourhood;
    private final Map<Long, Integer> dist;
    private final LinkedList<DictNode> Q;

    private DictGraph(int size) {
        this.neighbourhood = new ArrayList<DictNode>();
        this.dist = new HashMap<Long, Integer>();
        //this.prev = new int[size];
        this.Q = new LinkedList<DictNode>();
        this.nodes = new HashMap<Long, DictNode>(size);
    }

    /* =================================================================== *
     * PUBLIC
     * =================================================================== */


    public void reset() {
        this.nodes.clear();
    }

    public void add(Node n, example.cyclon.PeerSamplingService c) {
        DictNode node = new DictNode(n.getID());
        for (Node neighbor : c.getPeers()) {
            node.neighbors.add(neighbor.getID());
        }
        if (this.nodes.containsKey(n.getID())) throw new Error("should never happen");
        this.nodes.put(n.getID(), node);
    }

    public AvgReachablePaths avgReachablePaths(long v) {
        Map<Long, Integer> dist = dijkstra(nodes.get(v));

        AvgReachablePaths result = new AvgReachablePaths();

        double sum = 0;
        int reachable = 0;
        for (int d : dist.values()) {
            if (d != -1.0) {
                reachable += 1;
                sum += (double) d;
            }
        }

        if (reachable <= 1) {
            result.avg = 0;
        } else {
            result.avg = sum / (reachable - 1); // do not count ourselfs!
        }

        result.count = reachable;
        result.total = this.nodes.size();
        result.reachQuota = reachable / (double)result.total;


        return result;
    }

    public class AvgReachablePaths {
        public double avg;
        public int count;
        public int total;
        public double reachQuota;

        @Override
        public String toString() {
            return "avg:" + avg + "| %:" + reachQuota + " |count:" + this.count + " |total:" + total;
        }
    }

    public MeanPathLength meanPathLength() {
        MeanPathLength result = new MeanPathLength();
        result.maxReachQuota = Double.MIN_VALUE;
        result.minReachQuota = Double.MAX_VALUE;

        for (DictNode n : this.nodes.values()) {
            AvgReachablePaths avg = avgReachablePaths(n.id);
            result.avg += avg.avg;
            result.reachQuota += avg.reachQuota;
            if (avg.reachQuota > result.maxReachQuota) {
                result.maxReachQuota = avg.reachQuota;
            }
            if (avg.reachQuota < result.minReachQuota) {
                result.minReachQuota = avg.reachQuota;
            }
        }

        result.avg /= this.nodes.size();
        result.reachQuota /= this.nodes.size();

        return result;
    }

    public class MeanPathLength {
        public double avg;
        public double reachQuota;
        public double minReachQuota;
        public double maxReachQuota;

        @Override
        public String toString(){
            return "avg:" + avg + "| %:" + reachQuota + "| min%:" + minReachQuota + "| max%:" + maxReachQuota;
        }
    }

    /* =================================================================== *
     * PRIVATE
     * =================================================================== */


    public double localClusterCoefficient(DictNode v) {
        List<DictNode> N = neighbourhood(v);
        if (N.size() == 0) return 0;
        double possible = N.size() * (N.size() - 1);
        if (possible == 0) return 0;
        double actual = 0;
        for (DictNode a : N) {
            for (DictNode b : N) {
                if (a.id != b.id) {
                    if (areInterconnected(a.id, b.id)) {
                        actual += 1;
                    }
                }
            }
        }
        return actual / possible;
    }

    private boolean areInterconnected(long a, long b) {
        DictNode aNode = nodes.get(a);
        DictNode bNode = nodes.get(b);
        return in(a, bNode.neighbors) && in(b, aNode.neighbors);
    }

    /**
     * get the immediately connected neighbours:
     *
     * @param v
     * @return N_i = {v_j : e_ij \in E ^ e_ji \in E }
     */
    public List<DictNode> neighbourhood(DictNode v) {
        this.neighbourhood.clear();
        for (long n : v.neighbors) {
            if (hasDirectedConnection(n, v.id)) {
                this.neighbourhood.add(this.nodes.get(n));
            }
        }
        return this.neighbourhood;
    }

    private boolean hasDirectedConnection(long from, long to) {
        DictNode fromNode = this.nodes.get(from);
        return in(to, fromNode.neighbors);
    }

    private boolean in(long i, List<Long> list) {
        for (long n : list) {
            if (n == i) return true;
        }
        return false;
    }

    public Map<Long, Integer> dijkstra(DictNode src) {
        return dijkstra(src.id);
    }

    /**
     * @param src
     * @return
     */
    public Map<Long, Integer> dijkstra(long src) {
        dist.clear();
        Q.clear();

        final long source = src;
        final int INFINITY = -1;

        dist.put(source, 0);

        for (DictNode v : nodes.values()) {
            if (v.id != source) {
                dist.put(v.id, INFINITY);
            }
            Q.add(v);
        }

        while (Q.size() > 0) {
            DictNode u = min(Q, dist);
            if (u == null) break; // disconnected graph
            Q.remove(u);
            for (long v : u.neighbors) {
                int alt = dist.get(u.id) + 1;
                if (dist.get(v) == INFINITY || alt < dist.get(v)) {
                    dist.put(v, alt);
                }
            }
        }
        return dist;
    }

    private DictNode min(LinkedList<DictNode> Q, Map<Long, Integer> dist) {
        int m = Integer.MAX_VALUE;
        DictNode min = null;
        for (DictNode u : Q) {
            int distance = dist.get(u.id);
            if (distance >= 0 && m >= distance) {
                m = distance;
                min = u;
            }
        }
        //if (min == null && Q.size() > 0) {
        //    return Q.get(0);
        //}
        return min;
    }


}
