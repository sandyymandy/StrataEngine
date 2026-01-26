package engine.strata.client.render.util.rendercommand;

import engine.helios.BufferBuilder;
import engine.helios.MatrixStack;
import engine.helios.RenderLayer;
import engine.strata.client.StrataClient;
import engine.strata.client.render.model.StrataModel;
import engine.strata.client.render.renderer.MasterRenderer;
import engine.strata.client.render.util.RenderUtils;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class RenderCommandQueue {
    private List<RenderCommand> recordingList = new ArrayList<>();
    private List<RenderCommand> executionList = new ArrayList<>();
    private final Object lock = new Object();

    /**
     * Swaps the lists so the Render thread can process what was just recorded.
     * Called at the start of the Render loop.
     */
    public void swap() {
        synchronized (lock) {
            List<RenderCommand> temp = recordingList;
            recordingList = executionList;
            executionList = temp;
            // Clear the new recording list so it's ready for the next logic tick
            recordingList.clear();
        }
    }

    /**
     * Executes everything currently in the execution list.
     * Called by MasterRenderer on the Render Thread.
     */
    public void flush() {
        for (RenderCommand command : executionList) {
            command.render();
        }
    }

    /**
     * Called by Logic Thread to submit a command.
     */
    public void submit(RenderCommand command) {
        synchronized (lock) {
            recordingList.add(command);
        }
    }

    public void submitModel(StrataModel model, MatrixStack poseStack, RenderLayer layer, int overlayColor) {
        synchronized (lock) {
            recordingList.add(new ModelCommand(model, RenderUtils.getMatrixSnapshot(poseStack), layer, overlayColor));
        }
    }


    private static record ModelCommand(
            StrataModel model,
            Matrix4f pose, // A snapshot of the matrix
            RenderLayer layer,
            int overlay
    ) implements RenderCommand {

        @Override
        public void render() {
            // This runs on the Render Thread
            MasterRenderer mr = StrataClient.getInstance().getMasterRenderer();
            BufferBuilder builder = mr.getBuffer(layer);

            // We use the snapped pose instead of a live stack
            mr.getModelRenderer().render(model, pose, builder);
        }
    }
    
}