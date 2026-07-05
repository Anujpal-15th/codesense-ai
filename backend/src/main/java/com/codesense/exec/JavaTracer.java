package com.codesense.exec;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.Field;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.StackFrame;
import com.sun.jdi.StringReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.VoidValue;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.ExceptionEvent;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.event.MethodEntryEvent;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.ExceptionRequest;
import com.sun.jdi.request.MethodEntryRequest;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.StepRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Drives a JDI (Java Debug Interface) session against a sandboxed debuggee to
 * produce a step-by-step {@link ExecutionTrace}. Stateless/thread-safe as a
 * singleton bean - all per-execution mutable state lives in a local
 * {@link TraceSession} instance for the duration of a single {@link #trace} call.
 */
@Slf4j
@Service
class JavaTracer {

    private static final List<String> EXCLUDED_CLASS_PATTERNS =
            List.of("java.*", "javax.*", "jdk.*", "sun.*", "com.sun.*");

    private final TraceLimits limits;
    private final TraceValueConverter converter;

    JavaTracer(TraceLimits limits) {
        this.limits = limits;
        this.converter = new TraceValueConverter(limits);
    }

    ExecutionTrace trace(SandboxHandle handle, String mainClassName) {
        long startTime = System.currentTimeMillis();
        VirtualMachine vm = attach(handle);
        log.info("Trace timing: JDI attach={}ms", System.currentTimeMillis() - startTime);
        TraceSession session = new TraceSession();

        try {
            armClassPrepareWatch(vm, mainClassName);
            vm.resume();
            runEventLoop(vm, handle, session, startTime + limits.timeoutSeconds().toMillis());
        } finally {
            try {
                vm.dispose();
            } catch (VMDisconnectedException ignored) {
                // debuggee already gone
            }
        }

        // Catch any trailing output produced after the last captured step
        // (e.g. right before VM death) that wasn't attributed to a step yet.
        session.consoleOutput.append(handle.drainOutput());

        long elapsed = System.currentTimeMillis() - startTime;
        return new ExecutionTrace(
                session.steps,
                session.outcome,
                session.exceptionInfo,
                session.consoleOutput.toString(),
                session.truncated,
                session.steps.size(),
                elapsed
        );
    }

    private void runEventLoop(VirtualMachine vm, SandboxHandle handle, TraceSession session, long deadline) {
        EventQueue queue = vm.eventQueue();
        while (true) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                session.outcome = ExecutionOutcome.TIMED_OUT;
                return;
            }

            EventSet eventSet;
            try {
                eventSet = queue.remove(Math.min(remaining, 500));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (eventSet == null) {
                continue;
            }

            if (handleEventSet(vm, handle, eventSet, session)) {
                return;
            }

            try {
                eventSet.resume();
            } catch (VMDisconnectedException e) {
                return;
            }
        }
    }

    /**
     * @return true if the event loop should stop
     */
    private boolean handleEventSet(VirtualMachine vm, SandboxHandle handle, EventSet eventSet, TraceSession session) {
        for (Event event : eventSet) {
            if (event instanceof ClassPrepareEvent cpe) {
                armClassScopedRequests(vm, cpe, session);
            } else if (isTraceableStepEvent(event)) {
                LocatableEvent le = (LocatableEvent) event;
                TraceStep step = captureStep(session.steps.size(), le, handle, eventTypeOf(event));
                session.consoleOutput.append(step.consoleOutputDelta());
                session.steps.add(step);
                if (session.steps.size() >= limits.maxSteps()) {
                    session.truncated = true;
                    session.outcome = ExecutionOutcome.TRUNCATED;
                    return true;
                }
            } else if (event instanceof ExceptionEvent ee) {
                session.exceptionInfo = captureException(ee);
                session.outcome = ExecutionOutcome.EXCEPTION;
                return true;
            } else if (event instanceof VMDeathEvent || event instanceof VMDisconnectEvent) {
                return true;
            }
        }
        return false;
    }

    private VirtualMachine attach(SandboxHandle handle) {
        try {
            AttachingConnector connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
                    .filter(c -> c.transport().name().equals("dt_socket"))
                    .findFirst()
                    .orElseThrow(() -> new ExecutionFailedException("No socket AttachingConnector available"));

            Map<String, Connector.Argument> args = connector.defaultArguments();
            args.get("hostname").setValue(handle.host());
            args.get("port").setValue(String.valueOf(handle.port()));

            return connector.attach(args);
        } catch (IOException | IllegalConnectorArgumentsException e) {
            throw new ExecutionFailedException("Failed to attach JDI debugger to sandbox", e);
        }
    }

    private void armClassPrepareWatch(VirtualMachine vm, String mainClassName) {
        EventRequestManager erm = vm.eventRequestManager();
        ClassPrepareRequest request = erm.createClassPrepareRequest();
        request.addClassFilter(mainClassName + "*");
        request.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        request.enable();
    }

    private void armClassScopedRequests(VirtualMachine vm, ClassPrepareEvent event, TraceSession session) {
        if (session.requestsArmed) {
            return;
        }
        session.requestsArmed = true;

        EventRequestManager erm = vm.eventRequestManager();
        ThreadReference thread = event.thread();

        StepRequest stepRequest = erm.createStepRequest(thread, StepRequest.STEP_LINE, StepRequest.STEP_INTO);
        EXCLUDED_CLASS_PATTERNS.forEach(stepRequest::addClassExclusionFilter);
        stepRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        stepRequest.enable();

        MethodEntryRequest methodEntryRequest = erm.createMethodEntryRequest();
        EXCLUDED_CLASS_PATTERNS.forEach(methodEntryRequest::addClassExclusionFilter);
        methodEntryRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        methodEntryRequest.enable();

        MethodExitRequest methodExitRequest = erm.createMethodExitRequest();
        EXCLUDED_CLASS_PATTERNS.forEach(methodExitRequest::addClassExclusionFilter);
        methodExitRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        methodExitRequest.enable();

        ExceptionRequest exceptionRequest = erm.createExceptionRequest(null, false, true);
        exceptionRequest.setSuspendPolicy(EventRequest.SUSPEND_ALL);
        exceptionRequest.enable();
    }

    private boolean isTraceableStepEvent(Event event) {
        return event instanceof StepEvent || event instanceof MethodEntryEvent || event instanceof MethodExitEvent;
    }

    private String eventTypeOf(Event event) {
        if (event instanceof StepEvent) {
            return "LINE";
        }
        if (event instanceof MethodEntryEvent) {
            return "METHOD_ENTRY";
        }
        if (event instanceof MethodExitEvent) {
            return "METHOD_EXIT";
        }
        return "UNKNOWN";
    }

    private TraceStep captureStep(int stepIndex, LocatableEvent event, SandboxHandle handle, String eventType) {
        ThreadReference thread = event.thread();
        List<StackFrameSnapshot> callStack;
        try {
            callStack = thread.frames().stream()
                    .limit(limits.maxStackDepth())
                    .map(this::toSnapshot)
                    .toList();
        } catch (IncompatibleThreadStateException e) {
            callStack = List.of();
        }

        TraceValue returnValue = null;
        if (event instanceof MethodExitEvent mee) {
            Value rv = mee.returnValue();
            if (!(rv instanceof VoidValue)) {
                returnValue = converter.convert(rv);
            }
        }

        return new TraceStep(stepIndex, eventType, thread.name(), callStack, handle.drainOutput(), returnValue);
    }

    private StackFrameSnapshot toSnapshot(StackFrame frame) {
        Location loc = frame.location();
        String className = loc.declaringType().name();
        String methodName = loc.method().name();
        int lineNumber = loc.lineNumber();

        List<LocalVariable> visible;
        try {
            visible = frame.visibleVariables();
        } catch (AbsentInformationException e) {
            visible = List.of();
        }

        List<VariableSnapshot> vars = new ArrayList<>();
        for (LocalVariable lv : visible) {
            Value value = frame.getValue(lv);
            vars.add(new VariableSnapshot(lv.name(), lv.typeName(), converter.convert(value)));
        }

        ObjectReference thisObject = frame.thisObject();
        VariableSnapshot thisSnapshot = thisObject != null
                ? new VariableSnapshot("this", thisObject.referenceType().name(), converter.convert(thisObject))
                : null;

        return new StackFrameSnapshot(className, methodName, lineNumber, vars, thisSnapshot);
    }

    private ExceptionInfo captureException(ExceptionEvent event) {
        ObjectReference exceptionObj = event.exception();
        String className = exceptionObj.referenceType().name();
        String message = extractMessage(exceptionObj);

        List<String> stackLines = new ArrayList<>();
        try {
            List<StackFrame> frames = event.thread().frames();
            int limit = Math.min(frames.size(), limits.maxStackDepth());
            for (int i = 0; i < limit; i++) {
                Location loc = frames.get(i).location();
                String sourceName;
                try {
                    sourceName = loc.sourceName();
                } catch (AbsentInformationException e) {
                    sourceName = "Unknown Source";
                }
                stackLines.add("at " + loc.declaringType().name() + "." + loc.method().name()
                        + "(" + sourceName + ":" + loc.lineNumber() + ")");
            }
        } catch (IncompatibleThreadStateException e) {
            // thread wasn't in a suspended state we could read frames from; leave empty
        }

        return new ExceptionInfo(className, message, stackLines);
    }

    private String extractMessage(ObjectReference exceptionObj) {
        try {
            // Throwable.detailMessage is the field backing getMessage() - best-effort;
            // subclasses that override getMessage() to compute a different string
            // won't be reflected here.
            Field messageField = exceptionObj.referenceType().fieldByName("detailMessage");
            if (messageField == null) {
                return "";
            }
            Value msgValue = exceptionObj.getValue(messageField);
            return msgValue instanceof StringReference sr ? sr.value() : "";
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static class TraceSession {
        final List<TraceStep> steps = new ArrayList<>();
        final StringBuilder consoleOutput = new StringBuilder();
        ExecutionOutcome outcome = ExecutionOutcome.NORMAL;
        ExceptionInfo exceptionInfo;
        boolean truncated = false;
        boolean requestsArmed = false;
    }
}
