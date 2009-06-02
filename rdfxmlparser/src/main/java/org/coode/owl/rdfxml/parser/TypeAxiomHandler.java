package org.coode.owl.rdfxml.parser;

import org.semanticweb.owl.model.*;
import org.semanticweb.owl.vocab.OWLRDFVocabulary;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

/*
 * Copyright (C) 2006, University of Manchester
 *
 * Modifications to the initial code base are copyright of their
 * respective authors, or their employers as appropriate.  Authorship
 * of the modifications may be determined from the ChangeLog placed at
 * the end of this file.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.

 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.

 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */


/**
 * Author: Matthew Horridge<br>
 * The University Of Manchester<br>
 * Bio-Health Informatics Group<br>
 * Date: 11-Dec-2006<br><br>
 */
public class TypeAxiomHandler extends BuiltInTypeHandler {

    public TypeAxiomHandler(OWLRDFConsumer consumer) {
        super(consumer, OWLRDFVocabulary.OWL_AXIOM.getURI());
    }

    public TypeAxiomHandler(OWLRDFConsumer consumer, URI typeURI) {
        super(consumer, typeURI);
    }

    public boolean canHandleStreaming(URI subject, URI predicate, URI object) throws OWLException {
        // We can't handle this is a streaming fashion, because we can't
        // be sure that the subject, predicate, object triples have been parsed.
        return false;
    }

    /**
     * Gets the URI of the predicate of the triple that specifies the target of a reified axiom
     * @return The URI, by default this is owl:annotatedTarget
     */
    protected URI getTargetTriplePredicate() {
        return OWLRDFVocabulary.OWL_ANNOTATED_TARGET.getURI();
    }

    /**
     * Gets the URI of the predicate of the triple that specifies that predicate of a reified axiom
     * @return The URI, by default this is owl:annotatedProperty
     */
    protected URI getPropertyTriplePredicate() {
        return OWLRDFVocabulary.OWL_ANNOTATED_PROPERTY.getURI();
    }

    /**
     * Gets the URI of the predicate of the triple that specifies the source of a reified axiom
     * @return The URI, by default this is owl:annotatedSource
     */
    protected URI getSourceTriplePredicate() {
        return OWLRDFVocabulary.OWL_ANNOTATED_SOURCE.getURI();
    }


    public void handleTriple(URI subject, URI predicate, URI object) throws OWLException {
        consumeTriple(subject, predicate, object);


        URI annotatedSource = getObjectOfSourceTriple(subject);
        URI annotatedProperty = getObjectOfPropertyTriple(subject);
        URI annotatedTarget = getObjectOfTargetTriple(subject);
        OWLLiteral annotatedTargetLiteral = null;
        if(annotatedTarget == null) {
            annotatedTargetLiteral = getTargetLiteral(subject);
        }

        Set<OWLAnnotation> annotations = translateAnnotations(subject);
        getConsumer().setPendingAnnotations(annotations);
        if (annotatedTarget != null) {
            getConsumer().handle(annotatedSource, annotatedProperty, annotatedTarget);
        } else {
            getConsumer().handle(annotatedSource, annotatedProperty, annotatedTargetLiteral);
        }
        if (!annotations.isEmpty()) {
            OWLAxiom ax = getConsumer().getLastAddedAxiom();
            getConsumer().getOWLOntologyManager().applyChange(new RemoveAxiom(getConsumer().getOntology(), ax.getAxiomWithoutAnnotations()));
        }

    }

    protected OWLAxiom handleAxiomTriples(URI subjectTriple, URI predicateTriple, URI objectTriple, Set<OWLAnnotation> annotations) throws OWLException {
        // Reconstitute the original triple from the reification triples
        return getConsumer().getLastAddedAxiom();
    }


    protected OWLAxiom handleAxiomTriples(URI subjectTripleObject, URI predicateTripleObject, OWLLiteral con, Set<OWLAnnotation> annotations) throws OWLException {
        getConsumer().handle(subjectTripleObject, predicateTripleObject, con);
        return getConsumer().getLastAddedAxiom();
    }

    private Set<OWLAnnotation> translateAnnotations(URI subject) {
        Set<URI> predicates = getConsumer().getPredicatesBySubject(subject);
        predicates.remove(getSourceTriplePredicate());
        predicates.remove(getPropertyTriplePredicate());
        predicates.remove(getTargetTriplePredicate());
        // We don't handle rdf:subject, rdf:predicate and rdf:object as synonymns - they might be genuinely in the
        // ontology.
        predicates.remove(OWLRDFVocabulary.RDF_SUBJECT.getURI());
        predicates.remove(OWLRDFVocabulary.RDF_PREDICATE.getURI());
        predicates.remove(OWLRDFVocabulary.RDF_OBJECT.getURI());
        predicates.remove(OWLRDFVocabulary.RDF_TYPE.getURI());

        Set<OWLAnnotation> annotations = new HashSet<OWLAnnotation>();
        for (URI candidatePredicate : predicates) {
            getConsumer().isAnnotationProperty(candidatePredicate);
            annotations.addAll(getConsumer().translateAnnotations(subject));
        }
        return annotations;
    }

    private OWLLiteral getTargetLiteral(URI subject) throws OWLRDFXMLParserMalformedNodeException {
        OWLLiteral con = getConsumer().getLiteralObject(subject, getTargetTriplePredicate(), true);
        if (con == null) {
            con = getConsumer().getLiteralObject(subject, OWLRDFVocabulary.RDF_OBJECT.getURI(), true);
        }
        if (con == null) {
            throw new OWLRDFXMLParserMalformedNodeException("missing owl:annotatedTarget triple.");
        }
        return con;
    }


    /**
     * Gets the object of the target triple that has the specified main node
     * @param mainNode The main node
     * @return The object of the triple that has the specified mainNode as its subject and the URI returned
     * by the {@code TypeAxiomHandler#getSourceTriplePredicate()} method.  For backwards compatibility, a
     * search will also be performed for triples whos subject is the specified mainNode and predicate rdf:object
     */
    private URI getObjectOfTargetTriple(URI mainNode) {
        URI objectTripleObject = getConsumer().getResourceObject(mainNode, getTargetTriplePredicate(), true);
        if (objectTripleObject == null) {
            objectTripleObject = getConsumer().getResourceObject(mainNode, OWLRDFVocabulary.RDF_OBJECT.getURI(), true);
        }
        return objectTripleObject;
    }

    private URI getObjectOfPropertyTriple(URI subject) throws OWLRDFXMLParserMalformedNodeException {
        URI predicateTripleObject = getConsumer().getResourceObject(subject, getPropertyTriplePredicate(), true);
        if (predicateTripleObject == null) {
            predicateTripleObject = getConsumer().getResourceObject(subject, OWLRDFVocabulary.RDF_PREDICATE.getURI(), true);
        }
        if (predicateTripleObject == null) {
            throw new OWLRDFXMLParserMalformedNodeException("missing owl:annotatedProperty triple.");
        }
        return predicateTripleObject;
    }


    /**
     * Gets the source URI for an annotated or reified axiom
     *
     * @param mainNode The main node of the triple
     * @return The source object
     * @throws OWLRDFXMLParserMalformedNodeException
     *
     */
    private URI getObjectOfSourceTriple(URI mainNode) throws OWLRDFXMLParserMalformedNodeException {
        URI subjectTripleObject = getConsumer().getResourceObject(mainNode, getSourceTriplePredicate(), true);
        if (subjectTripleObject == null) {
            subjectTripleObject = getConsumer().getResourceObject(mainNode, OWLRDFVocabulary.RDF_SUBJECT.getURI(), true);
        }
        if (subjectTripleObject == null) {
            throw new OWLRDFXMLParserMalformedNodeException("missing owl:annotatedSource triple.");
        }
        return subjectTripleObject;
    }


}
