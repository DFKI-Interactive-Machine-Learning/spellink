package fitpack;

public class Spline {
    public double[] t; // knots, array of size n+k+1
    public double[][] c; // spline coefficients, shape>=n
    public int k; //B-spline order
    public double[] u; // An array of the values of the parameter

    public Spline(double[] t, double[][] c, int k, double[] u) {
        this.t = t;
        this.c = c;
        this.k = k;
        this.u = u;
    }
}
