/* Demand-materialized C++03 library ownership. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;
/** The used library slice. Headers own declarations; include edges never own headers. */
public final class StandardLibrary extends Node {
    List<SourceFile> headers = List.of();
    public List<SourceFile> headers() { return headers; }
    @Override public List<Node> children() { return List.copyOf(headers); }
    @Override public void accept(NodeVisitor visitor) { visitor.visit(this); }
    @Override public Builder edit() { return new EditContext().edit(this, Builder.class); }
    @Override Builder copyBuilder(EditContext c) { var b = new Builder(); c.register(this,b); b.headers=c.copyAll(headers,SourceFile.class); return b; }
    public static final class Builder extends NodeBuilder<StandardLibrary> {
        List<NodeBuilder<? extends SourceFile>> headers=List.of();
        Builder() { super(StandardLibrary.class); }
        @Override StandardLibrary newNode() { return new StandardLibrary(); }
        @Override void fillNode(StandardLibrary n, BuildContext c) { n.headers=c.copyAll(headers); }
    }
}
