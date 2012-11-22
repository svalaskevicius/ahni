package ojc.ahni.hyperneat;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;

import org.apache.log4j.Logger;
import org.jgapcustomised.Allele;
import org.jgapcustomised.Chromosome;
import org.jgapcustomised.ChromosomeMaterial;
import org.jgapcustomised.InvalidConfigurationException;

import com.anji.integration.Activator;
import com.anji.integration.ActivatorTranscriber;
import com.anji.integration.AnjiNetTranscriber;
import com.anji.integration.Transcriber;
import com.anji.integration.TranscriberException;
import com.anji.neat.ConnectionAllele;
import com.anji.neat.NeatChromosomeUtility;
import com.anji.neat.NeatConfiguration;
import com.anji.neat.NeuronAllele;
import com.anji.neat.NeuronGene;
import com.anji.neat.NeuronType;
import com.anji.nn.activationfunction.StepActivationFunction;
import com.anji.nn.activationfunction.GaussianActivationFunction;
import com.anji.util.Configurable;
import com.anji.util.Properties;

/**
 * Extension of NEAT configuration with HyperNEAT-specific features added:<ul>
 * <li>Forces correct number of inputs and outputs according to the number required for the CPPN in the initial {@link org.jgapcustomised.ChromosomeMaterial} sample from which an initial population is generated.</li>
 * <li>Adds required hidden nodes to the initial {@link org.jgapcustomised.ChromosomeMaterial} sample from which an initial population is generated if {@link ojc.ahni.hyperneat.HyperNEATTranscriber#HYPERNEAT_LEO_LOCALITY} is enabled.</li>
 * </ul>  
 * 
 * @author Oliver Coleman
 */
public class HyperNEATConfiguration extends NeatConfiguration implements Configurable {
	private static final Logger logger = Logger.getLogger(HyperNEATConfiguration.class);
	private static final long serialVersionUID = 1L;

	/**
	 * The number of evolution runs to perform. If this is more than one then it is highly recommended that random.seed is not set
	 * so that a unique seed can be generated for each run,
	 */
	public static final String NUM_RUNS_KEY = "num.runs";
	
	/**
	 * Where to save files generated by one or more runs.
	 */
	public static final String OUTPUT_DIR_KEY = "output.dir";
	
	/**
	 * Whether to display experiment information graphically.
	 */
	public static final String ENABLE_VISUALS_KEY = "visuals.enable";
	
	private Properties props;
	
	private boolean enableVisuals;

	public void init(Properties newProps) throws InvalidConfigurationException {
		super.init(newProps);
		props = newProps;

		Transcriber transcriber = (Transcriber) props.singletonObjectProperty(ActivatorTranscriber.TRANSCRIBER_KEY);
		if (transcriber instanceof HyperNEATTranscriber) {
			HyperNEATTranscriber hnTranscriber = ((HyperNEATTranscriber) transcriber);
			short stimulusSize = hnTranscriber.getCPPNInputCount();
			short responseSize = hnTranscriber.getCPPNOutputCount();
			
			ChromosomeMaterial sample;
			// If LEO locality seeding is enabled. 
			if (hnTranscriber.leoEnabled() && props.getBooleanProperty(HyperNEATTranscriber.HYPERNEAT_LEO_LOCALITY, false)) {
				boolean fullyConnected = props.getBooleanProperty(INITIAL_TOPOLOGY_FULLY_CONNECTED_KEY, false);
				if (fullyConnected) {
					logger.warn("It's generally best not to have a fully connected initial CPPN topology (" + INITIAL_TOPOLOGY_FULLY_CONNECTED_KEY + "=true) when LEO locality seeding is enabled (" + HyperNEATTranscriber.HYPERNEAT_LEO_LOCALITY + "=true).");
				}
				if (props.containsKey(INITIAL_TOPOLOGY_NUM_HIDDEN_NEURONS_KEY) && props.getShortProperty(INITIAL_TOPOLOGY_NUM_HIDDEN_NEURONS_KEY) > 0) {
					logger.warn("Ignoring specified hidden neuron count for CPPN (" + INITIAL_TOPOLOGY_NUM_HIDDEN_NEURONS_KEY + ") because LEO locality seeding is enabled (" + HyperNEATTranscriber.HYPERNEAT_LEO_LOCALITY + "=true).");
				}
				
				List<Allele> sampleAlleles = NeatChromosomeUtility.initAlleles(stimulusSize, (short) 0, responseSize, this, fullyConnected);
				
				// Add hidden nodes for locality seed.
				int[] idxSrc = new int[]{hnTranscriber.getCPPNIndexSourceX(), hnTranscriber.getCPPNIndexSourceY(), hnTranscriber.getCPPNIndexSourceZ()};
				int[] idxTrg = new int[]{hnTranscriber.getCPPNIndexTargetX(), hnTranscriber.getCPPNIndexTargetY(), hnTranscriber.getCPPNIndexTargetZ()};
				int[] idxLEO = hnTranscriber.getCPPNIndexLEO();
				// The order of input and output neuron alleles will match those in a CPPN generated from the Chromosomes.  
				List<NeuronAllele> inputNeuronAlleles = new ArrayList(NeatChromosomeUtility.getNeuronMap(sampleAlleles, NeuronType.INPUT).values());
				List<NeuronAllele> outputNeuronAlleles = new ArrayList(NeatChromosomeUtility.getNeuronMap(sampleAlleles, NeuronType.OUTPUT).values());
				
				// Create step function neuron.
				NeuronAllele stepNeuron = newNeuronAllele(NeuronType.HIDDEN, StepActivationFunction.NAME);
				sampleAlleles.add(stepNeuron);
				// Connect step function neuron to each leo output.
				for (int sourceLayer = 0; sourceLayer < idxLEO.length; sourceLayer++) {
					NeuronAllele leoOutput = outputNeuronAlleles.get(idxLEO[sourceLayer]);
					sampleAlleles.add(newConnectionAllele(stepNeuron.getInnovationId(), leoOutput.getInnovationId(), 1));
				}
				
				// Create Gaussian neurons for each source and target pair for x, y and z coordinate inputs.
				int stepNeuronBiasValue = 0;
				for (int i = 0; i < 3; i++) {
					if (idxSrc[i] != -1 && idxTrg[i] != -1) {
						NeuronAllele newNeuron = newNeuronAllele(NeuronType.HIDDEN, GaussianActivationFunction.NAME);
						sampleAlleles.add(newNeuron);
						NeuronAllele coordSrc = inputNeuronAlleles.get(idxSrc[i]);
						NeuronAllele coordTrg = inputNeuronAlleles.get(idxTrg[i]);
						
						// Connect it to source and target coordinate inputs.
						sampleAlleles.add(newConnectionAllele(coordSrc.getInnovationId(), newNeuron.getInnovationId(), 1));
						sampleAlleles.add(newConnectionAllele(coordTrg.getInnovationId(), newNeuron.getInnovationId(), -1));
						
						// Connect it to stepNeuron.
						sampleAlleles.add(newConnectionAllele(newNeuron.getInnovationId(), stepNeuron.getInnovationId(), 1));
						
						stepNeuronBiasValue--; // Bias value for stepNeuron depends on number of inputs; 
					}
				}
				
				// Add bias to stepNeuron.
				sampleAlleles.add(newConnectionAllele(inputNeuronAlleles.get(hnTranscriber.getCPPNIndexBiasInput()).getInnovationId(), stepNeuron.getInnovationId(), stepNeuronBiasValue));
			
				sample = new ChromosomeMaterial(sampleAlleles);
			}
			else {
				sample = NeatChromosomeUtility.newSampleChromosomeMaterial(stimulusSize, props.getShortProperty(INITIAL_TOPOLOGY_NUM_HIDDEN_NEURONS_KEY, DEFAULT_INITIAL_HIDDEN_SIZE), responseSize, this, props.getBooleanProperty(INITIAL_TOPOLOGY_FULLY_CONNECTED_KEY, true));
			}
			setSampleChromosomeMaterial(sample);
		}
		
		enableVisuals = props.getBooleanProperty(ENABLE_VISUALS_KEY, false);
	}
	
	/**
	 * @see HyperNEATConfiguration#init(Properties)
	 */
	public HyperNEATConfiguration() {
		super();
	}

	/**
	 * See <a href=" {@docRoot} /params.htm" target="anji_params">Parameter Details </a> for specific property settings.
	 * 
	 * @param newProps
	 * @see HyperNEATConfiguration#init(Properties)
	 * @throws InvalidConfigurationException
	 */
	public HyperNEATConfiguration(Properties newProps) throws InvalidConfigurationException {
		super(newProps);
	}
	
	public boolean visualsEnabled() {
		return enableVisuals;
	}
	
	/**
	 * Factory method to construct a new neuron allele with unique innovation ID.
	 * @param type The type of neuron allele to create
	 * @param activationFunctionType The activation function for the neuron. "random" may be supplied to specify that a function should be randomly selected.
	 * @return NeuronAllele
	 */
	protected NeuronAllele newNeuronAllele(NeuronType type, String activationFunctionType) {
		if (activationFunctionType.equals("random")) {
			activationFunctionType = hiddenActivationTypeRandomAllowed[getRandomGenerator().nextInt(hiddenActivationTypeRandomAllowed.length)];
		}
		NeuronGene gene = new NeuronGene(type, nextInnovationId(), activationFunctionType);
		return new NeuronAllele(gene);
	}
}