/* Abstract C++03 library iterator facts, without ABI or pointer assumptions. MIT License. */
package com.kniazkov.cpg.analysis;
import com.kniazkov.cpg.model.*;
import java.util.*;

/** Facts come exclusively from a trusted concrete catalogue vector instance. */
public final class LibraryIteratorSemantics {
    private LibraryIteratorSemantics() {}
    public record Info(OpaqueLibraryType iterator, TypeNode element, boolean readOnly, boolean reverse) {
        public ClassDeclaration container() { return iterator.iteratorContainer().orElseThrow(); }
        public boolean compatible(Info other) { return reverse==other.reverse && container().typeIdentity().equals(other.container().typeIdentity()); }
        public TypeNode referenceType() { return readOnly ? QualifiedType.create().setBaseType(element.edit()).setConst(true).build() : element; }
    }
    public static Optional<Info> info(TypeNode type) {
        type=TypeSemantics.unqualified(TypeSemantics.withoutReference(type));
        if(type instanceof OpaqueLibraryType opaque && LibraryDeclarations.isLibrary(opaque)
                && opaque.iteratorContainer().filter(LibraryDeclarations::isLibrary).isPresent()) {
            var owner=opaque.iteratorContainer().orElseThrow();
            if(!owner.name().equals("vector") || owner.templateArguments().isEmpty())return Optional.empty();
            return owner.templateArguments().getFirst().type().filter(element->!element.canonicalIdentity().equals("bool"))
                    .map(element->new Info(opaque,element,opaque.name().equals("const_iterator"),false));
        }
        if(type instanceof NamedType named && named.declaration().orElse(null) instanceof ClassDeclaration owner
                && LibraryDeclarations.isLibrary(owner) && owner.name().equals("reverse_iterator") && !owner.templateArguments().isEmpty())
        {
            // Keep the written substituted argument: catalogue iterator typedefs
            // are type arguments even when parser lookahead classified them as templates.
            var arguments=named.templateArguments().isEmpty()?owner.templateArguments():named.templateArguments();
            return arguments.getFirst().type().flatMap(LibraryIteratorSemantics::info)
                    .map(base->new Info(base.iterator(),base.element(),base.readOnly(),!base.reverse()));
        }
        return Optional.empty();
    }
}
