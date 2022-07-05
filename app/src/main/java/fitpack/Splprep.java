package fitpack;

import f.fitpack.Parcur;
import f.fitpack.Splder;
import f.fitpack.Splev;
import org.netlib.util.doubleW;
import org.netlib.util.intW;

import java.util.Arrays;

import static java.lang.Math.max;
import static java.lang.Math.sqrt;

public class Splprep {
    /**
     * @param x    A list of sample vector arrays representing the curve.
     * @param w    Strictly positive rank-1 array of weights the same length as `x[0]`.
     *             The weights are used in computing the weighted least-squares spline
     *             fit. If the errors in the `x` values have standard-deviation given by
     *             the vector d, then `w` should be 1/d. Default is ``ones(len(x[0]))``.
     * @param u    An array of parameter values. If not given, these values are
     *             calculated automatically as ``M = len(x[0])``, where
     *             <p>
     *             v[0] = 0
     *             <p>
     *             v[i] = v[i-1] + distance(`x[i]`, `x[i-1]`)
     *             <p>
     *             u[i] = v[i] / v[M-1]
     * @param ub   The end-points of the parameters interval.  Defaults to
     *             u[0] and u[-1].
     * @param ue   The end-points of the parameters interval.  Defaults to
     *             u[0] and u[-1].
     * @param k    Degree of the spline. Cubic splines are recommended.
     *             Even values of `k` should be avoided especially with a small s-value.
     *             ``1 <= k <= 5``, default is 3.
     * @param task only task==0 currently supported.
     *             If task==0 (default), find t and c for a given smoothing factor, s.
     *             If task==1, find t and c for another value of the smoothing factor, s.
     *             There must have been a previous call with task=0 or task=1
     *             for the same set of data.
     *             If task=-1 find the weighted least square spline for a given set of
     *             knots, t.
     * @param s    A smoothing condition.  The amount of smoothness is determined by
     *             satisfying the conditions: ``sum((w * (y - g))**2,axis=0) <= s``,
     *             where g(x) is the smoothed interpolation of (x,y).  The user can
     *             use `s` to control the trade-off between closeness and smoothness
     *             of fit.  Larger `s` means more smoothing while smaller values of `s`
     *             indicate less smoothing. Recommended values of `s` depend on the
     *             weights, w.  If the weights represent the inverse of the
     *             standard-deviation of y, then a good `s` value should be found in
     *             the range ``(m-sqrt(2*m),m+sqrt(2*m))``, where m is the number of
     *             data points in x, y, and w.
     * @param t    The knots needed for task=-1.
     * @param nest An over-estimate of the total number of knots of the spline to
     *             help in determining the storage space.  By default nest=m/2.
     *             Always large enough is nest=m+k+1.
     * @param per  If non-zero, data points are considered periodic with period
     *             ``x[m-1] - x[0]`` and a smooth periodic spline approximation is
     * @return
     */
    public static Spline splprep(double[][] x, double[] w, double[] u, Double ub, Double ue, int k, int task, Double s, double[] t, Integer nest, int per) {
//        't': array([], float), 'wrk': array([], float),
//        'iwrk': array([], intc), 'u': array([], float),
//        'ub': 0, 'ue': 1}
        int idim = 2; //TODO: add miltidim
        int m = x[0].length;
        if (per > 0) throw new IllegalArgumentException("per>0 not supported yet");
        if (!((1 <= k) && (k <= 5))) throw new IllegalArgumentException(String.format("1 <= k <=5 must hold, k=%o", k));
        if (task != 0) throw new IllegalArgumentException("only task == 0 supported");
        if (t == null) {
            t = new double[0];
        }
        if (w == null) {
            w = new double[m];
            Arrays.fill(w, 1);
        }

        int ipar = 0;
        if (u != null) ipar = 1;
        if (u != null) {
            if (ub == null) ub = u[0];
            if (ue == null) ue = u[u.length - 1];
        } else {
            u = new double[m];
            Arrays.fill(w, 0);
        }
        if (s == null) s = m - sqrt(2 * m);
        int n = 0; //t.length - t not supported yet
        if (nest == null) nest = m + 2 * k;
        if ((task >= 0 && s == 0) || (nest < 0)) {
            if (per > 0) nest = m + 2 * k;
            else nest = m + k + 1;
        }
        nest = max(nest, 2 * k + 3);
        double[] wrk = null;
        double[] iwrk = null;
        double[] xx = ravel(x);
        ParcurResult res = parcurWrapper(xx, w, u, ub, ue, k, task, ipar, s, t, nest, wrk, iwrk, per);
        if (res.ier > 0) {
            throw new IllegalStateException("An error occured, errcode: " + res.ier);
        }
        return new Spline(res.t, res.c, k, res.u);
    }

    private static double[] ravel(double[][] x) {
        int rows = x.length; //expected 2
        int cols = x[0].length;
        double[] xx = new double[rows * cols];
        for (int j = 0; j < cols; j++) {
            for (int i = 0; i < rows; i++) {
                xx[i + j * rows] = x[i][j];
            }
        }
        return xx;
    }

    /**
     * @return
     */
    private static ParcurResult parcurWrapper(double[] x, double[] w, double[] u, Double _ub, Double _ue,
                                              int k, int iopt, int ipar, Double s, double[] t_py,
                                              Integer nEst, double[] wrk_py, double[] iwrk_py, int per) {
        /*
        t, c, o = _fitpack._parcur(ravel(transpose(x)), w, u, ub, ue, k,
        task, ipar, s, t, nest, wrk, iwrk, per)
        */
        //params
        int m = w.length;
        int mx = x.length;
        int idim = mx / m; //curve dim,  2 by defauls
        int lwrk = 0;
        if (per > 0) { // per>0 not supported now
            lwrk = m * (k + 1) + nEst * (7 + idim + 5 * k);
        } else { //default
            lwrk = m * (k + 1) + nEst * (6 + idim + 3 * k);
        }

        int nc = idim * nEst;
        int lwa = nc + 2 * nEst + lwrk;
        double[] t = new double[lwa]; // t pointer to array
        double[] c = new double[nc];
        double[] wrk = new double[lwrk]; // wrk pointer to a part of array t starting from nest + idim*nest
        int[] iwrk = new int[nEst]; //integer array of dimension at least (nest)., iwrk_py
        intW n = new intW(0);
        int no = 0;
        if (iopt == 0) {
            n.val = t_py.length;
            no = n.val;
            for (int i = 0; i < n.val; i++) {
                t[i] = t_py[i];
            }
        }

        if (iopt == 1) {
            throw new IllegalArgumentException("iopt=1 not supported");
        }
        if (per > 0) {
            throw new IllegalArgumentException("per!=0 Periodic splines not supported");
        }
        doubleW fp = new doubleW(0);
        intW ier = new intW(0);
        doubleW ub = new doubleW(_ub);
        doubleW ue = new doubleW(_ue);
        Parcur.parcur(iopt, ipar, idim, m, u, 0, mx, x, 0, w, 0, ub, ue, k, s, nEst, n, t, 0, nc, c, 0, fp, wrk, 0, lwrk, iwrk, 0, ier);

        // ier - error message
        if (ier.val == 10) {
            throw new IllegalStateException("Invalid inputs.");
        }
        if (ier.val > 0 && n.val == 0) {
            n.val = 1;
        }
        int lc = (n.val - k - 1) * idim;

        double[] ap_t = Arrays.copyOfRange(t, 0, n.val);
        double[][] ap_c = new double[idim][n.val - k - 1];
        for (int i = 0; i < idim; i++)
            for (int j = 0; j < n.val - k - 1; j++)
                ap_c[i][j] = c[j + i * n.val];
        double[] ap_wrk = Arrays.copyOfRange(wrk, 0, n.val);
        int[] ap_iwrk = Arrays.copyOfRange(iwrk, 0, n.val);
        return new ParcurResult(ap_t, ap_c, u, ub.val, ue.val, ap_wrk, ap_iwrk, ier.val, fp.val);
    }

    /**
     * @param x
     * @return
     */
    public static double[][] splev(double[] x, Spline tck, int der, int ext) {
        double[] coord1 = tck.c[0];
        double[] coord2 = tck.c[1];
        double[][] res = new double[2][x.length];
        res[0] = splevWrapper(x, tck.t, coord1, tck.k, der, ext);
        res[1] = splevWrapper(x, tck.t, coord2, tck.k, der, ext);
        return res;
    }

    private static double[] splevWrapper(double[] x, double[] t, double[] c, int k, int der, int ext) {
        int m = x.length;
        int n = t.length;
        double[] y = new double[m];
        intW ier = new intW(0);
        if (der > 0) {
            double[] wrk = new double[n];
            Splder.splder(t, 0, n, c, 0, k, der, x, 0, y, 0, m, ext, wrk, 0, ier);
        } else
            Splev.splev(t, 0, n, c, 0, k, x, 0, y, 0, m, ext, ier);
        if (ier.val > 0)
            throw new IllegalStateException("Error while calculating splev: " + ier.val);
        return y;

    }
}
