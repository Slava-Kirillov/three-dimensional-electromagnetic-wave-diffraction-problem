package ru.diploma.data;

import ru.diploma.calc.CellIntegralCalc;
import ru.diploma.calc.FuncCalcOnCellImpl;
import ru.diploma.calc.OperatorCalculation;
import ru.diploma.config.EqConfig;
import ru.diploma.data.complex.Complex;
import ru.diploma.data.complex.ComplexVector;
import ru.diploma.service.AbstractExecutorService;
import ru.diploma.util.DataUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

public class SystemOfLinearEquations extends AbstractExecutorService {

    private Complex[][] matrix_of_coefficient;
    private Complex[] constant_term;

    public SystemOfLinearEquations(float[][][] cells, CellVectors cellVectors, float[][] collocationPoints, EqConfig config) {
        this.matrix_of_coefficient = new Complex[cells.length * 2][cells.length * 2];
        this.constant_term = new Complex[cells.length * 2];

        Complex wave_num_complex = new Complex(config.getWave_number(), 0.0f);

        float angleRadian = (float) (config.getAnglePhi()*Math.PI/180);

        this.calcCoefficientOfMatrix(cells, cellVectors, collocationPoints, wave_num_complex,
                this.getWaveVector(wave_num_complex, angleRadian), this.getComplexAmplitude(config));
        this.executor.shutdown();
    }

    /**
     * Расчет матрицы коэффициентов СЛАУ
     *
     * @param cells             - Ячейки, на которые разбито исследуеме тело
     * @param cellVectors       - Ортонормированный базис для ячеек
     * @param collocationPoints - Точки коллокации для ячеек
     * @return
     */
    private void calcCoefficientOfMatrix(float[][][] cells, CellVectors cellVectors, float[][] collocationPoints,
                                         Complex wave_num_complex, ComplexVector waveVector, ComplexVector complexAmplitude) {
        float eps = getEps(cells);

        float[][] normals = cellVectors.getNormal();
        float[][][] tau_vectors = new float[2][cells.length][3];
        tau_vectors[0] = cellVectors.getTau1();
        tau_vectors[1] = cellVectors.getTau2();

        int cores = Runtime.getRuntime().availableProcessors();
        int totalNum = cells.length;

        int numIterForTask = totalNum / cores;

        this.executor = Executors.newFixedThreadPool(cores);

//        cores = 1;

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < cores; i++) {
            int start = numIterForTask * i;
            int end;
            if (i == cores - 1) {
                end = totalNum;
            } else {
                end = numIterForTask * (i + 1);
            }

            Callable<Boolean> task = () -> parallelTask(start, end, cells, tau_vectors, eps, collocationPoints,
                    wave_num_complex, waveVector, complexAmplitude, normals);
            tasks.add(task);
        }

        List<Boolean> result = execute(tasks);
        if (result.stream().anyMatch(entry -> !entry)) {
            System.out.println("Tasks completed with error");
        }
    }

    /**
     * Задача для параллельного выполнения
     *
     * @param start
     * @param end
     * @param cells
     * @param tau_vectors
     * @param eps
     * @param collocationPoints
     * @param wave_num_complex
     * @return
     */
    private Boolean parallelTask(int start, int end, float[][][] cells, float[][][] tau_vectors, float eps,
                                 float[][] collocationPoints, Complex wave_num_complex, ComplexVector waveVector,
                                 ComplexVector complexAmplitude, float[][] normals) {
        System.out.println("Task started. Thread: " + Thread.currentThread().getId());

        int g = 5;
        int vecDim = 3;

        for (int i = start; i < end; i++) {
            ComplexVector collocPointVector = getVectorOfCollocPoint(collocationPoints[i]);
            ComplexVector externalField = getExternalField(complexAmplitude, waveVector, collocPointVector);
            ComplexVector normal = new ComplexVector(normals[i][0], normals[i][1], normals[i][2]);
            ComplexVector normalInverse = new ComplexVector(-normals[i][0], -normals[i][1], -normals[i][2]);


            ComplexVector vecMult = ComplexVector.vecMultiply(normalInverse, externalField);

            for (int k = 0; k < cells.length; k++) {
                float[][] cell = cells[k];
                float[] x = collocationPoints[i];
                ComplexVector integral = CellIntegralCalc.cellIntegralCalculation(x, cell, eps, wave_num_complex, g, vecDim);

                for (int m = 0; m < 2; m++) {
                    int ii = 2 * i + m; // номер строки для вставки в matrix
                    for (int l = 0; l < 2; l++) {

                        ComplexVector tau_l_k = new ComplexVector(tau_vectors[l][k][0], tau_vectors[l][k][1], tau_vectors[l][k][2]);
                        ComplexVector tau_m_i = new ComplexVector(tau_vectors[m][i][0], tau_vectors[m][i][1], tau_vectors[m][i][2]);

                        ComplexVector a = ComplexVector.vecMultiply(tau_l_k, integral);
                        Complex R = ComplexVector.scalarMultiply(ComplexVector.vecMultiply(normal, a), tau_m_i);

                        if (i == k && m == l) {
                            R.add(new Complex(0.5f, 0.0f));
                        }
                        int kk = 2 * k + l; // номер столбца для вставки в matrix
                        this.matrix_of_coefficient[ii][kk] = R;
                    }
                    ComplexVector tau = new ComplexVector(tau_vectors[m][i][0], tau_vectors[m][i][1], tau_vectors[m][i][2]);
                    constant_term[ii] = ComplexVector.scalarMultiply(vecMult, tau);
                }
            }

        }

        System.out.println("Task ended. Thread: " + Thread.currentThread().getId());
        return true;
    }

//    /**
//     * Расчет свободных коэффициентов СЛАУ
//     *
//     * @param cellVectors       - Вектора для ячеук
//     * @param collocationPoints - Точки коллокации для яцеек
//     * @param waveVector        - Волновое число для внешнего электромагнитного поля
//     * @param complexAmplitude  - Комплексная амплитуда внешнего эелектормагнитного поля
//     * @return
//     */
//    private void calcConstantTerm(CellVectors cellVectors,
//                                  float[][] collocationPoints,
//                                  ComplexVector waveVector,
//                                  ComplexVector complexAmplitude) {
//        float[][] normals = cellVectors.getNormal();
//        float[][][] tau_vectors = new float[2][normals.length][3];
//        tau_vectors[0] = cellVectors.getTau1();
//        tau_vectors[1] = cellVectors.getTau2();
//
//        for (int i = 0; i < normals.length; i++) {
//            ComplexVector collocPointVector = getVectorOfCollocPoint(collocationPoints[i]);
//            ComplexVector externalField = getExternalField(complexAmplitude, waveVector, collocPointVector);
//            ComplexVector normal = new ComplexVector(-normals[i][0], -normals[i][1], -normals[i][2]);
//
//            ComplexVector vecMult = ComplexVector.vecMultiply(normal, externalField);
//            for (int m = 0; m < 2; m++) {
//                ComplexVector tau = new ComplexVector(tau_vectors[m][i][0], tau_vectors[m][i][1], tau_vectors[m][i][2]);
//
//                int ii = 2 * i + m;
//                constant_term[ii] = ComplexVector.scalarMultiply(tau, vecMult);
//            }
//        }
//
//    }

    /**
     * Вычисление внешнего электромагнитного поля в точке коллокации Eext = E0 * exp(i*k*r)
     * E0 - произвольная комплексная амплитуда
     * k - волновой вектор падающей волны
     * r - радиус вектор точки коллокации
     * i - мнимая единица
     *
     * @param complexAmplitude  - E0
     * @param waveVector        - k
     * @param collocPointVector - r
     * @return
     */
    private ComplexVector getExternalField(ComplexVector complexAmplitude,
                                           ComplexVector waveVector,
                                           ComplexVector collocPointVector) {
        Complex imagUnit = new Complex(0.0f, 1.0f);

        Complex a1 = ComplexVector.scalarMultiply(waveVector, collocPointVector);
        Complex a2 = Complex.multiply(imagUnit, a1);

        Complex exp = Complex.exp(a2);
        return ComplexVector.multiply(exp, complexAmplitude);

    }

    /**
     * Получить радиус вектор точки коллокации в виде вектора с комплексными координатами
     *
     * @param collocPoint - координаты точки коллокации
     * @return
     */
    private ComplexVector getVectorOfCollocPoint(float[] collocPoint) {
        return new ComplexVector(collocPoint[0], collocPoint[1], collocPoint[2]);
    }

    /**
     * Расчет коэффициентов матрицы СЛАУ
     *
     * @param i
     * @param k
     * @param m
     * @param l
     * @param cell_k
     * @param tau_k_l
     * @param tau_i_m
     * @param x_i
     * @param eps
     * @param wave_num
     * @return
     */
//    private Complex getCoefficient(int i, int k, int m, int l, float[][] cell_k, float[] tau_k_l, float[] tau_i_m, ComplexVector normal, float[] x_i, float eps, Complex wave_num) {
//        int g = 5;// разбиение стороны ячейки
//
//        float additional_coef = 0.0f;
//        if (i == k && m == l) {
//            additional_coef = 0.5f;
//        }
//
//        Complex additional_coef_complex = new Complex(additional_coef, 0.0f);
//
//        ComplexVector tau_i_m_complex = new ComplexVector(tau_i_m[0], tau_i_m[1], tau_i_m[2]);
//        ComplexVector tau_k_l_complex = new ComplexVector(tau_k_l[0], tau_k_l[1], tau_k_l[2]);
//
//        ComplexVector operator_R = OperatorCalculation.operatorCalc(x_i, cell_k, eps, wave_num, g, tau_k_l_complex, 3);
//
//        return Complex.add(additional_coef_complex, ComplexVector.scalarMultiply(ComplexVector.vecMultiply(normal, operator_R), tau_i_m_complex));
//    }

    /**
     * Расчет параметра эпсилон, для сглаживания особенности в ядре интегрального оператора
     *
     * @param cells - Ячейки, на которые разбито исследуеме тело
     * @return
     */
    private float getEps(float[][][] cells) {
        int numCoordinates = cells[0][0].length;
        float h = -1;

        for (float[][] cell : cells) {
            CellDiagonals diags = DataUtils.getDiagOfCell(cell, numCoordinates);
            float diag1 = DataUtils.getVectorNorma(diags.getDiag1());
            float diag2 = DataUtils.getVectorNorma(diags.getDiag2());

            if (h == -1) {
                h = diag1;
            }
            if (diag1 < h) {
                h = diag1;
            }
            if (diag2 < h) {
                h = diag2;
            }
        }
        return 2 * h;
    }

    /**
     * Получить вектор комплексной амплитуды внешнего электромагнитного поля
     *
     * @return
     */
    private ComplexVector getComplexAmplitude(EqConfig config) {
        return new ComplexVector(config.getEx(), config.getEy(), config.getEz());
    }

    private ComplexVector getWaveVector(Complex wave_number, float anglePhi) {
        double sinPhi = Math.sin(anglePhi);
        double cosPhi = Math.cos(anglePhi);
        return new ComplexVector(
                Complex.multiply((float) -cosPhi, wave_number),
                Complex.multiply((float) -sinPhi, wave_number),
                new Complex()
        );
    }

    @Override
    protected String getTasksBatchName() {
        return "Matrix calculation";
    }

    //Getters
    public Complex[][] getMatrix_of_coefficient() {
        return this.matrix_of_coefficient;
    }

    public Complex[] getConstant_term() {
        return this.constant_term;
    }
}
