package com.wc3model2mc.api.client;

import com.wc3model2mc.mdx.model.MdxRenderModel;

import java.io.IOException;

/** Converts one discovered MDX/BLP folder into renderer-ready data. */
@FunctionalInterface
public interface MdxResourceDecoder {
    MdxRenderModel decode(Wc3ModelResourceBundle resources) throws IOException;
}
