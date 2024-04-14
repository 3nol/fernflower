package org.jetbrains.java.decompiler.main;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.rels.ClassWrapper;
import org.jetbrains.java.decompiler.main.rels.MethodWrapper;
import org.jetbrains.java.decompiler.modules.decompiler.exps.ExitExprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;
import org.jetbrains.java.decompiler.modules.decompiler.exps.FieldExprent;
import org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statement;
import org.jetbrains.java.decompiler.modules.decompiler.stats.Statements;
import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.struct.StructRecordComponent;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.util.Objects;
import java.util.stream.Collectors;

public final class RecordProcessor {
  public static void clearRecord(ClassWrapper wrapper) {
    StructClass cl = wrapper.getClassStruct();

    // check all methods for synthetic constructors and record component getters
    for (MethodWrapper method : wrapper.getMethods()) {
      StructMethod mt = method.methodStruct;
      String name = mt.getName();
      String descriptor = mt.getDescriptor();

      // (1) hide default constructor with the same signature as record fields
      // (2) hide synthetic, non-overwritten getters (need to check code to verify)
      if (CodeConstants.INIT_NAME.equals(mt.getName()) && isSyntheticConstructor(cl, mt)
        || isSyntheticFieldGetter(cl, mt, method.root)) {
        wrapper.getHiddenMembers().add(InterpreterUtil.makeUniqueKey(name, descriptor));
      }
    }
  }

  /*
   * Checks whether this constructor (aka '<init>' method) represents the synthetic constructor
   * generated for the record to initialize its components.
   *
   * Matches joined descriptors of record components against method descriptor.
   */
  private static boolean isSyntheticConstructor(StructClass cl, StructMethod mt) {
    String recordComponentsDescriptor = cl.getRecordComponents().stream()
      .map(StructRecordComponent::getDescriptor)
      .collect(Collectors.joining());
    return String.format("(%s)V", recordComponentsDescriptor).equals(mt.getDescriptor());
  }

  /*
   * Checks whether a getter was generated synthetically. That is, not overwritten by the user
   * which may be bad practise, but is still possible.
   *
   * Tries to find a matching record component for the getter method by matching name and descriptor.
   * However, this is not enough to identify such a synthetic constructor. We need to check that the
   * code precisely contains the following.
   *
   * ```
   * public <TYPE> <NAME>() {
   *   return this.<NAME>;
   * }
   * ```
   */
  private static boolean isSyntheticFieldGetter(StructClass cl, StructMethod mt, RootStatement root) {
    StructRecordComponent matchingComponent = null;
    for (StructRecordComponent component : cl.getRecordComponents()) {
      if (mt.getName().equals(component.getName())
        && mt.getDescriptor().equals(String.format("()%s", component.getDescriptor()))) {
        matchingComponent = component;
      }
    }

    Statement firstData = Statements.findFirstData(root);
    if (Objects.isNull(matchingComponent)
      || Objects.isNull(firstData)
      || Objects.requireNonNull(firstData.getExprents()).isEmpty()) {
      return false;
    }
    return matchComponentToExprent(cl, matchingComponent, firstData.getExprents().get(0));
  }

  private static boolean matchComponentToExprent(StructClass cl, StructRecordComponent component, Exprent exprent) {
    if (exprent.type != Exprent.EXPRENT_EXIT
      || ((ExitExprent) exprent).getExitType() != ExitExprent.EXIT_RETURN) {
      return false;
    }
    Exprent returnExprent = ((ExitExprent) exprent).getValue();
    if (returnExprent.type != Exprent.EXPRENT_FIELD) {
      return false;
    }
    FieldExprent fieldExprent = ((FieldExprent) returnExprent);
    return fieldExprent.getClassname().equals(cl.qualifiedName)
      && fieldExprent.getName().equals(component.getName())
      && fieldExprent.getDescriptor().descriptorString.equals(component.getDescriptor());
  }
}
