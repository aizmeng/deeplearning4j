/*-
 *
 *  * Copyright 2015 Skymind,Inc.
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 *
 */

package org.deeplearning4j.nn.layers;

import lombok.*;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.api.MaskState;
import org.deeplearning4j.nn.api.activations.Activations;
import org.deeplearning4j.nn.api.layers.LayerConstraint;
import org.deeplearning4j.nn.conf.CacheMode;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.optimize.api.ConvexOptimizer;
import org.deeplearning4j.optimize.api.IterationListener;
import org.nd4j.linalg.api.memory.MemoryWorkspace;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;

import java.util.*;

/**
 * A layer with input and output, no parameters or gradients
 */
@Data
@NoArgsConstructor
public abstract class AbstractLayer<LayerConfT extends org.deeplearning4j.nn.conf.layers.Layer> implements Layer {

    protected INDArray input;
    @Getter(AccessLevel.NONE) @Setter(AccessLevel.NONE)
    protected INDArray preOutput;
    protected NeuralNetConfiguration conf;
    protected boolean dropoutApplied = false;
    protected int index = 0;
    protected INDArray maskArray;
    protected MaskState maskState;
    protected CacheMode cacheMode = CacheMode.NONE;

    protected int iterationCount;
    protected int epochCount;

    public AbstractLayer(NeuralNetConfiguration conf) {
        this.conf = conf;
        cacheMode = conf.getCacheMode();
    }

    public AbstractLayer(NeuralNetConfiguration conf, INDArray input) {
        this(conf);
        this.input = input;
    }

    @Override
    public int numInputs() {
        return 1;
    }

    @Override
    public int numOutputs() {
        return 1;
    }

    @Override
    public String getName(){
        return conf.getLayer().getLayerName();
    }

    @Override
    public void setInputs(INDArray... inputs){
        if(inputs == null) {
            input = null;
            return;
        }

        if(inputs.length != 1){
            throw new IllegalArgumentException("Cannot set itputs: 1 input expected, got " + inputs.length);
        }
        this.input = inputs[0];
    }

    @Override
    public void setCacheMode(CacheMode mode) {
        if (mode == null)
            mode = CacheMode.NONE;

        this.cacheMode = mode;
    }

    protected LayerConfT layerConf() {
        return (LayerConfT) this.conf.getLayer();
    }

    protected String layerId() {
        String name = this.conf().getLayer().getLayerName();
        return "(layer name: " + (name == null ? "\"\"" : name) + ", layer index: " + index + ")";
    }

    @Override
    public void setInput(int inputNumber, INDArray input){
        if(inputNumber != 0) throw new IllegalArgumentException("Index must be 0: got " + inputNumber);
        this.input = input;
        dropoutApplied = false;
    }

    @Override
    public INDArray getInput(int inputNumber){
        if(inputNumber != 0) throw new IllegalArgumentException("Index must be 0: got " + inputNumber);
        return this.input;
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public void setIndex(int index) {
        this.index = index;
    }

    @Override
    public void update(Gradient gradient) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setConf(NeuralNetConfiguration conf) {
        this.conf = conf;
    }

    /**Returns the parameters of the neural network as a flattened row vector
     * @return the parameters of the neural network
     */
    @Override
    public INDArray params() {
        return null;
    }

    @Override
    public INDArray getParam(String param) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void setParam(String key, INDArray val) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void setParams(INDArray params) {
        if (params != null) {
            throw new UnsupportedOperationException("Not supported");
        }
    }

    protected void setParams(INDArray params, char order) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void setParamsViewArray(INDArray params) {
        if (params != null) {
            throw new UnsupportedOperationException("Not supported");
        }
    }

    @Override
    public INDArray getGradientsViewArray() {
        return null;
    }

    @Override
    public void setBackpropGradientsViewArray(INDArray gradients) {
        if (gradients != null) {
            throw new UnsupportedOperationException("Not supported");
        }
    }

    @Override
    public void setParamTable(Map<String, INDArray> paramTable) {
        if (paramTable != null && !paramTable.isEmpty()) {
            throw new UnsupportedOperationException("Not supported");
        }
    }

    @Override
    public Map<String, INDArray> paramTable() {
        return paramTable(false);
    }

    @Override
    public Map<String, INDArray> paramTable(boolean backpropParamsOnly) {
        return Collections.emptyMap();
    }

    protected void applyMask(INDArray to) {
        to.muliColumnVector(maskArray);
    }

    @Override
    public Activations activate(Activations input, boolean training) {
        setInput(input.get(0));
        return activate(training);
    }

    @Override
    public Activations activate(Activations input){
        return activate(input, false);
    }

    @Override
    public double calcL2(boolean backpropParamsOnly) {
        return 0.0;
    }

    @Override
    public double calcL1(boolean backpropParamsOnly) {
        return 0.0;
    }

    @Override
    public int batchSize() {
        return input.size(0);
    }

    @Override
    public NeuralNetConfiguration conf() {
        return conf;
    }


    @Override
    public void clear() {
        input = null;
    }

    protected void applyDropOutIfNecessary(boolean training){//} int iteration, int epoch) {
        if(training && !dropoutApplied && layerConf().getIDropout() != null ){
            //TODO: Epoch + iteration counters...
            if (Nd4j.getWorkspaceManager().checkIfWorkspaceExists(ComputationGraph.workspaceExternal)) {
                try (MemoryWorkspace ws = Nd4j.getWorkspaceManager()
                        .getWorkspaceForCurrentThread(ComputationGraph.workspaceExternal)
                        .notifyScopeBorrowed()) {
                    input = layerConf().getIDropout().applyDropout(input, getIterationCount(), getEpochCount(), false);
                }
            } else {
                input = layerConf().getIDropout().applyDropout(input, getIterationCount(), getEpochCount(), false);
            }
            dropoutApplied = true;
        }
    }

    /**
     * The number of parameters for the model
     *
     * @return the number of parameters for the model
     */
    @Override
    public int numParams() {
        return 0;
    }

    @Override
    public int numParams(boolean backwards) {
        return numParams();
    }

    @Override
    public INDArray input() {
        return input;
    }

    @Override
    public void setInputMiniBatchSize(int size) {}

    @Override
    public int getInputMiniBatchSize() {
        return input.size(0);
    }

    @Override
    public void setMaskArray(int idx, INDArray maskArray) {
        if(idx != 0)
            throw new IllegalStateException("Index must be 0");

        this.maskArray = maskArray;
    }

    @Override
    public INDArray getMaskArray(int idx) {
        if(idx != 0)
            throw new IllegalStateException("Index must be 0");
        return maskArray;
    }


//    @Override
//    public Pair<INDArray, MaskState> feedForwardMaskArray(INDArray maskArray, MaskState currentMaskState,
//                    int minibatchSize) {
//        //Most layers: CNN, dense, activation, etc - set mask array, mask state and then leave the mask unmodified
//
//        this.maskArray = maskArray;
//        this.maskState = currentMaskState;
//
//        return new Pair<>(maskArray, currentMaskState);
//    }


    @Override
    public Gradient gradient() {
        throw new UnsupportedOperationException(
                        "Not supported for this layer, or should be overridden for layers requiring it");
    }


    @Override
    public void applyConstraints(int iteration, int epoch){
        if(layerConf().getConstraints() != null){
            for(LayerConstraint lc : layerConf().getConstraints()){
                lc.applyConstraint(this, iteration, epoch);
            }
        }
    }
}