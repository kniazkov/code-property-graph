/* Abstract C++03 library iterator facts, without ABI or pointer assumptions. MIT License. */
package com.kniazkov.cpg.analysis;
import com.kniazkov.cpg.model.*;
import java.util.*;

/** Facts come exclusively from a trusted concrete catalogue container instance. */
public final class LibraryIteratorSemantics {
    private LibraryIteratorSemantics() {}
    public record Info(OpaqueLibraryType iterator, TypeNode element, boolean readOnly, boolean reverse) {
        public ClassDeclaration container() { return iterator.iteratorContainer().orElseThrow(); }
        public boolean randomAccess() { return Set.of("vector","deque").contains(container().name()); }
        public boolean compatible(Info other) { return reverse==other.reverse && container().typeIdentity().equals(other.container().typeIdentity()); }
        public TypeNode referenceType() { return readOnly ? QualifiedType.create().setBaseType(element.edit()).setConst(true).build() : element; }
    }
    public static Optional<Info> info(TypeNode type) {
        type=TypeSemantics.unqualified(TypeSemantics.withoutReference(type));
        if(type instanceof OpaqueLibraryType opaque && LibraryDeclarations.isLibrary(opaque)
                && opaque.iteratorContainer().filter(LibraryDeclarations::isLibrary).isPresent()) {
            var owner=opaque.iteratorContainer().orElseThrow();
            if(!Set.of("vector","deque","list","set","multiset","map","multimap").contains(owner.name()) || owner.templateArguments().isEmpty())return Optional.empty();
            Optional<TypeNode> element = Set.of("map","multimap").contains(owner.name())
                    ? opaque.iteratorValueType()
                    : owner.templateArguments().getFirst().type();
            return element.filter(t->!owner.name().equals("vector") || !t.canonicalIdentity().equals("bool"))
                    .map(t->new Info(opaque,t,opaque.name().equals("const_iterator") || Set.of("set","multiset").contains(owner.name()),false));
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
