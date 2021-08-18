package models.swarming;

import simudyne.core.abm.GlobalState;
import simudyne.core.annotations.Input;

public final class Globals extends GlobalState {

  /* ---------- number of agents ----------- */
  @Input(name = "Tick Count")
  public int tickCount = 0;

  @Input(name = "Wealth")
  public double wealth = 8000; /* 300 (depends on the start price. aim at CT run out of  no money around 70% of the simulation)
                                    8000 when start price = 50
                                    6000 when start price = 30 (good shape)
                                  */

  @Input(name = "Number of FT Traders")
  public int numFundamentalTrader = 10; // expect: 10

  @Input(name = "Number of Momentum Traders")
  public int numMomentumTrader = 10;

  @Input(name = "Number of Noise Traders")
  public int numNoiseTrader = 0; // 8-10 seems ok

  @Input(name = "Number of Coordinated Traders")
  public int numCoordinatedTrader = 10;

  @Input(name = "Number of influencers")
  public int numInfluencer = 1;

  @Input(name = "Number of Hedge Fund")
  public int numHedgeFund = 1; // maybe 2 HF

  @Input(name = "Number of social media")
  public int numSocialMedia = 1; // expect 1

  @Input(name = "Number of Exchange")
  public int numExchange = 1; // expect 1

  @Input(name = "Number of Data Provider")
  public int numDataProvider = 1; // expect 1


  /* ---------- number of agents ----------- */

  /* aka price elasticity. speed at which the market price converges market equilibrium */
  @Input(name = "Exchange's lambda")
  public double lambda = 0.5; // 0.3?, 0.025

  @Input(name = "Uncertainty of true value")
  public double sigma_u = 10;

  /* for market true value */
  @Input(name = "Standard deviation of Exogenous Effect")
  public double sigma_v = 0.075;

  @Input(name = "Std. dev. of Jump Diffusion")
  public double sigma_jd = 1;

  @Input(name = "Jump Diffusion's Lambda")
  public double lambda_jd = 1;

  @Input(name = "Demand of noise traders")
  public double sigma_n = 4;

  /* for market true value */
  @Input(name = "standard deviation of CT")
  public double sigma_ct = 2; // was 0.5. 1 should be better

  @Input(name = "Sensitivity to market")
  public double ftParam_kappa = 0.08; // aka. sensitivity (0.5 before)

  @Input(name = "MT Params: alpha")
  public double mtParams_alpha = 0.5; // or 0.8, 0.9 or 0.5 (to be safe)

  @Input(name = "MT Params: Beta")
  public double mtParams_beta = 1; // 10 before, (0.1 in paper)

  @Input(name = "MT Params: gamma")
  public double mtParams_gamma = 50;

  /* smaller the value, more clusters formed */
  @Input(name = "vicinity Range")
  public double vicinityRange = 6; /*
                                        2: converge to close to 0
                                        above 2: all converge to 3
                                     */

  /* speed of convergence of opinions */
  @Input(name = "Trust to average of other opinions")
  public double gamma = 0.05; // was 0.025

  /* larger k, lower speed of convergence */
  @Input(name = "influencer weighting")
  public double k = 10;

  @Input(name = "Opinion of influencer")
  public double influencerOpinion = 4;

  @Input(name = "Opinion of Coordinated T")
  public double ctOpinion = 5;

  /* 0-1, larger number means a higher probability to trade in step */
  @Input(name = "Probability of noise trade")
  public double pNoiseTrade = 1;

  /* 0-1, larger number means a higher probability to trade in step */
  @Input(name = "Probability of momentum trade")
  public double pMomentumTrade = 1;

  @Input(name = "Probability of coordinated trade")
  public double pCoordinatedTrade = 1;

  @Input(name = "tick to kick start opinion exchange")
  public double tickToStartOpDyn = 20;

  /* ---------- newly added after code review --------------- */
  @Input(name = "weighting for delta opinion")
  public double weightingForDeltaOpinion = 0.01;

  @Input(name = "trader demand Multiplier")
  public double multiplier = 1.05;

  @Input(name = "Pareto distribution param")
  public int pareto_params = 3;

  @Input(name = "Influencer weighting")
  public double influencer_k = 0.5;

  @Input(name = "Confidence Factor's weighting")
  public double cf_weighting = 100;

  @Input(name = "weighting for influencers")
  public double multiplier_i = 0.05;





}
