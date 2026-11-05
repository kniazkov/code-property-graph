/* Code Property Graph: endpoints of intraprocedural flow edges. MIT License. */
package com.kniazkov.cpg.model;

/** A control-flow endpoint. Syntax nodes retain their identity in the project snapshot. */
public sealed interface FlowVertex permits Node, AuxiliaryFlowVertex {}
