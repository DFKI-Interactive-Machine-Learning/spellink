package fitpack;

public class ParcurResult {
    public double[] t;
    public double[][] c;
    public double[] u;
    public double ub;
    public double ue;
    public double[] wrk;
    public int[] iwrk;
    public int ier;
    public double fp;

    public ParcurResult(double[] t, double[][] c, double[] u, double ub, double ue, double[] wrk, int[] iwrk, int ier, double fp) {
        this.t = t;
        this.c = c;
        this.u = u;
        this.ub = ub;
        this.ue = ue;
        this.wrk = wrk;
        this.iwrk = iwrk;
        this.ier = ier;
        this.fp = fp;
    }
}
