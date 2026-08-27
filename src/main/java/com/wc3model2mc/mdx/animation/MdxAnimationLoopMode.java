package com.wc3model2mc.mdx.animation;

import com.wc3model2mc.mdx.model.MdxSequence;

/** Controls whether playback follows the MDX sequence flag, loops, or plays once. */
public enum MdxAnimationLoopMode {
    MODEL_DEFAULT,
    LOOP,
    ONCE;

    public boolean isLooping(MdxSequence sequence) {
        return switch (this) {
            case MODEL_DEFAULT -> sequence.looping();
            case LOOP -> true;
            case ONCE -> false;
        };
    }
}
