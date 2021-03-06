package com.anji.nn.activationfunction;

/**
 * Divide activation function (divides first input by second input).
 * 
 * @author Oliver Coleman
 */
public class DivideActivationFunction implements ActivationFunction, ActivationFunctionNonIntegrating {

	/**
	 * identifying string
	 */
	public final static String NAME = "divide";

	/**
	 * @see Object#toString()
	 */
	public String toString() {
		return NAME;
	}

	/**
	 * This class should only be accessd via ActivationFunctionFactory.
	 */
	DivideActivationFunction() {
		// no-op
	}

	/**
	 * Not used, returns 0.
	 */
	@Override
	public double apply(double input) {
		return 0;
	}

	/**
	 * Return first input divided by second input (or just first input if no second input).
	 */
	@Override
	public double apply(double[] input, double bias) {
		if (input.length < 2)
			return input[0];
		if (input[1] == 0)
			return Float.MAX_VALUE * Math.signum(input[0]);
		double v = input[0] / input[1];
		return Double.isNaN(v) ? 0 : v;
	}

	/**
	 * @see com.anji.nn.activationfunction.ActivationFunction#getMaxValue()
	 */
	public double getMaxValue() {
		return Float.MAX_VALUE;
	}

	/**
	 * @see com.anji.nn.activationfunction.ActivationFunction#getMinValue()
	 */
	public double getMinValue() {
		return 0;
	}

	/**
	 * @see com.anji.nn.activationfunction.ActivationFunction#cost()
	 */
	public long cost() {
		return 42;
	}
}
