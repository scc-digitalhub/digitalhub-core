package it.smartcommunitylabdhub.fsm;

// @SpringBootTest(classes = { StateMachineTest.class })
public class StateMachineTest {
    // // some method definition
    // // @Test
    // public void fsm() {
    //     // Create the state machine
    //     FsmState<String, String, Map<String, Object>> state1 = new FsmState<>();
    //     FsmState<String, String, Map<String, Object>> state2 = new FsmState<>();
    //     FsmState<String, String, Map<String, Object>> state3 = new FsmState<>();
    //     FsmState<String, String, Map<String, Object>> state4 = new FsmState<>();
    //     FsmState<String, String, Map<String, Object>> errorState = new FsmState<>(); // Error
    //     // state
    //     // Create the initial state and context
    //     String initialState = "State1";
    //     Map<String, Object> initialContext = new HashMap<>();
    //     // Create the state machine using the builder
    //     Fsm.Builder<String, String, Map<String, Object>> builder = Fsm
    //         .<String, String, Map<String, Object>>builder(initialState, initialContext)
    //         .withState(
    //             "State1",
    //             new FsmState.StateBuilder<String, String, Map<String, Object>>("State1")
    //                 .withTransition(new Transition<>("Event1", "State2", (context, input) -> true))
    //                 .build()
    //         )
    //         .withState(
    //             "State2",
    //             new FsmState.StateBuilder<String, String, Map<String, Object>>("State2")
    //                 .withTransition(new Transition<>("Event2", "State3", (context, input) -> true))
    //                 .build()
    //         )
    //         .withState(
    //             "State3",
    //             new FsmState.StateBuilder<String, String, Map<String, Object>>("State3")
    //                 .withTransition(new Transition<>("Event3", "State4", (context, input) -> true))
    //                 .build()
    //         )
    //         .withState(
    //             "State4",
    //             new FsmState.StateBuilder<String, String, Map<String, Object>>("State4")
    //                 .withTransition(new Transition<>("Event4", "State1", (context, input) -> true))
    //                 .build()
    //         )
    //         .withStateChangeListener((newState, context) ->
    //             System.out.println("State Change Listener: " + newState + ", context: " + context)
    //         );
    //     // Add event listeners
    //     builder.withEventListener(
    //         "Event1",
    //         (context, input) -> System.out.println("Event1 Listener: context: " + context)
    //     );
    //     builder.withEventListener(
    //         "Event2",
    //         (context, input) -> System.out.println("Event2 Listener: context: " + context)
    //     );
    //     builder.withEventListener(
    //         "Event3",
    //         (context, input) -> System.out.println("Event3 Listener: context: " + context)
    //     );
    //     builder.withEventListener(
    //         "Event4",
    //         (context, input) -> System.out.println("Event4 Listener: context: " + context)
    //     );
    //     // Build the state machine
    //     Fsm<String, String, Map<String, Object>> stateMachine = builder.build();
    //     // here set internal logic
    //     stateMachine
    //         .getState("State1")
    //         .getTransition("Event1")
    //         .setInternalLogic((context, input, fs) -> {
    //             System.out.println("Executing internal logic of State1 with context: " + context);
    //             Optional.ofNullable(context).ifPresent(c -> c.put("value", 1));
    //             return Optional.of(1);
    //         });
    //     stateMachine
    //         .getState("State2")
    //         .getTransition("Event2")
    //         .setInternalLogic((context, input, fs) -> {
    //             System.out.println("Executing internal logic of State2 with context: " + context);
    //             Optional.ofNullable(context).ifPresent(c -> c.put("value", 1));
    //             return Optional.of("Hellow World");
    //         });
    //     // Trigger events to test the state machine
    //     Optional<Integer> result = stateMachine.goToState("State2", null);
    //     Optional<String> res = stateMachine.goToState("State3", null);
    //     stateMachine.goToState("State4", null);
    //     //        // Set internal logic for state 1
    //     //        state1.setInternalLogic((context, input, stateMachine) -> {
    //     //            System.out.println("Executing internal logic of State1 with context: " + context);
    //     //            Optional.ofNullable(context).ifPresent(c -> c.put("value", 1));
    //     //
    //     //            return Optional.of("State1 Result");
    //     //        });
    //     //        state1.setExitAction(context -> {
    //     //            System.out.println("exit action for state 1");
    //     //        });
    //     //        // Set internal logic for state 2
    //     //        state2.setInternalLogic((context, input, stateMachine) -> {
    //     //            System.out.println("Executing internal logic of State2 with  context: " + context);
    //     //            Optional.ofNullable(context).ifPresent(c -> c.put("value", 2));
    //     //            return Optional.of("State2 Result");
    //     //        });
    //     //
    //     //        // Set internal logic for state 3
    //     //        state3.setInternalLogic((context, input, stateMachine) -> {
    //     //            System.out.println("Executing internal logic of State3 with context: " + context);
    //     //            Optional.ofNullable(context).ifPresent(c -> c.put("value", 3));
    //     //            return Optional.of("State3 Result");
    //     //        });
    //     //
    //     //        // Set internal logic for state 4
    //     //        state4.setInternalLogic((context, input, stateMachine) -> {
    //     //            System.out.println("Executing internal logic of State4 with  context: " + context);
    //     //            Optional.ofNullable(context).ifPresent(c -> c.put("value", 4));
    //     //            return Optional.of("State4 Result");
    //     //        });
    //     //
    //     //        // Set internal logic for the error state
    //     //        errorState.setInternalLogic((context, input, stateMachine) -> {
    //     //            System.out.println("Error state reached. context: " + context);
    //     //            // Handle error logic here
    //     //            return Optional.empty(); // No result for error state
    //     //        });
    // }
}
