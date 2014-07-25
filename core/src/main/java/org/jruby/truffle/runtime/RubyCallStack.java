/*
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.jruby.truffle.runtime;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import org.jruby.truffle.nodes.RubyRootNode;
import org.jruby.truffle.runtime.core.RubyBasicObject;
import org.jruby.truffle.runtime.core.RubyModule;
import org.jruby.truffle.runtime.methods.RubyMethod;

public abstract class RubyCallStack {

    public static void dump(final RubyContext context, Node currentNode) {
        CompilerAsserts.neverPartOfCompilation();

        System.err.println("call stack ----------");

        if (currentNode != null) {
            System.err.println("    at " + currentNode.getEncapsulatingSourceSection());
        }

        dumpFrame(context, Truffle.getRuntime().getCurrentFrame());

        Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Void>() {

            @Override
            public Void visitFrame(FrameInstance frameInstance) {
                dumpFrame(context, frameInstance);
                return null;
            }

        });

        System.err.println("---------------------");
    }

    private static void dumpFrame(RubyContext context, FrameInstance frame) {
        String sourceInfo = frame.getCallNode().getEncapsulatingSourceSection().toString();
        System.err.print("\tfrom " + sourceInfo + " in " + formatMethodName(frame));

        MaterializedFrame f = frame.getFrame(FrameInstance.FrameAccess.MATERIALIZE, false).materialize();
        FrameDescriptor fd = f.getFrameDescriptor();
        boolean first = true;
        for (Object ident : fd.getIdentifiers()) {
            if (ident instanceof String) {
                RubyBasicObject value = context.getCoreLibrary().box(f.getValue(fd.findFrameSlot(ident)));
                // TODO(CS): slow path send
                String repr = value.send("inspect", null).toString();
                if (first) {
                    first = false;
                    System.err.print(" with ");
                } else {
                    System.err.print(", ");
                }
                int maxLength = 12;
                if (repr.length() > maxLength) {
                    repr = repr.substring(0, maxLength) + "... (" + value.getRubyClass().getName() + ")";
                }
                System.err.print(ident + " = " + repr);
            }
        }
        System.err.println();
    }

    public static String formatMethodName(FrameInstance frame) {
        String methodName;
        RubyMethod method = getMethod(frame);
        if (method == null) {
            methodName = "<main>";
        } else {
            RubyModule module = method.getDeclaringModule();
            if (module == null) {
                methodName = "?#" + method.getName();
            } else {
                methodName = module.getName() + "#" + method.getName();
            }
        }
        return methodName;
    }

    public static FrameInstance getCallerFrame() {
        return Truffle.getRuntime().getCallerFrame();
    }

    public static Frame getCallerFrame(FrameInstance.FrameAccess access, boolean slowPath) {
        return getCallerFrame().getFrame(access, slowPath);
    }

    public static RubyMethod getCurrentMethod() {
        CompilerAsserts.neverPartOfCompilation();

        RubyMethod method;

        final FrameInstance currentFrame = Truffle.getRuntime().getCurrentFrame();

        method = getMethod(currentFrame);

        if (method != null) {
            return method;
        }

        method = Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<RubyMethod>() {

            @Override
            public RubyMethod visitFrame(FrameInstance frameInstance) {
                return getMethod(frameInstance);
            }

        });

        if (method != null) {
            return method;
        }

        throw new UnsupportedOperationException();
    }

    private static RubyMethod getMethod(FrameInstance frame) {
        CompilerAsserts.neverPartOfCompilation();

        if (frame == null) {
            return null;
        }

        final CallTarget callTarget = frame.getCallTarget();

        if (!(callTarget instanceof RootCallTarget)) {
            return null;
        }

        final RootCallTarget rootCallTarget = (RootCallTarget) callTarget;

        final RootNode rootNode = rootCallTarget.getRootNode();

        if (!(rootNode instanceof RubyRootNode)) {
            return null;
        }

        final RubyRootNode rubyRootNode = (RubyRootNode) rootNode;

        return RubyMethod.getMethod(rubyRootNode.getSharedMethodInfo());
    }


    public static RubyModule getCurrentDeclaringModule() {
        CompilerAsserts.neverPartOfCompilation();

        return getCurrentMethod().getDeclaringModule();
    }

    public static String getRubyStacktrace(){
        final StringBuilder stack = new StringBuilder();

        Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Void>() {

            @Override
            public Void visitFrame(FrameInstance frameInstance) {
                stack.append("from " + frameInstance.getCallNode().getEncapsulatingSourceSection() + "\n");
                return null;
            }

        });

        try {
            return String.format("%s at %s \n %s", getCurrentMethod().getName(), getCurrentMethod().getSharedMethodInfo().getSourceSection().getShortDescription(), stack);
        } catch(UnsupportedOperationException ex) {
            return String.format("(root) at %s:%s", getFilename(), getLineNumber());
        }
    }

    public static String getFilename(){
        return getCallerFrame().getCallNode().getEncapsulatingSourceSection().getSource().getName();
    }

    public static int getLineNumber(){
        return getCallerFrame().getCallNode().getEncapsulatingSourceSection().getStartLine();
    }

}
