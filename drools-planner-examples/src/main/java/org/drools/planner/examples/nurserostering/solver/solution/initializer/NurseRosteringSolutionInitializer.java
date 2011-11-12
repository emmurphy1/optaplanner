/*
 * Copyright 2010 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.planner.examples.nurserostering.solver.solution.initializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.builder.CompareToBuilder;
import org.drools.FactHandle;
import org.drools.WorkingMemory;
import org.drools.planner.core.phase.custom.CustomSolverPhaseCommand;
import org.drools.planner.core.score.buildin.hardandsoft.DefaultHardAndSoftScore;
import org.drools.planner.core.score.Score;
import org.drools.planner.core.solution.director.SolutionDirector;
import org.drools.planner.examples.common.domain.PersistableIdComparator;
import org.drools.planner.examples.nurserostering.domain.ShiftAssignment;
import org.drools.planner.examples.nurserostering.domain.Employee;
import org.drools.planner.examples.nurserostering.domain.NurseRoster;
import org.drools.planner.examples.nurserostering.domain.Shift;
import org.drools.planner.examples.nurserostering.domain.ShiftDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NurseRosteringSolutionInitializer implements CustomSolverPhaseCommand {

    protected final transient Logger logger = LoggerFactory.getLogger(getClass());

    public void changeWorkingSolution(SolutionDirector solutionDirector) {
        NurseRoster nurseRoster = (NurseRoster) solutionDirector.getWorkingSolution();
        initializeAssignmentList(solutionDirector, nurseRoster);
    }

    private void initializeAssignmentList(SolutionDirector solutionDirector,
            NurseRoster nurseRoster) {
        List<Employee> employeeList = nurseRoster.getEmployeeList();
        WorkingMemory workingMemory = solutionDirector.getWorkingMemory();

        // TODO the planning entity list from the solution should be used and might already contain initialized entities
        List<ShiftAssignment> shiftAssignmentList = createAssignmentList(nurseRoster);
        for (ShiftAssignment shiftAssignment : shiftAssignmentList) {
            FactHandle assignmentHandle = null;
            Score bestScore = DefaultHardAndSoftScore.valueOf(Integer.MIN_VALUE, Integer.MIN_VALUE);
            Employee bestEmployee = null;
            for (Employee employee : employeeList) {
                shiftAssignment.setEmployee(employee);
                if (assignmentHandle == null) {
                    assignmentHandle = workingMemory.insert(shiftAssignment);
                } else {
                    workingMemory.update(assignmentHandle, shiftAssignment);
                }
                Score score = solutionDirector.calculateScoreFromWorkingMemory();
                if (score.compareTo(bestScore) > 0) {
                    bestScore = score;
                    bestEmployee = employee;
                }
            }
            if (bestEmployee == null) {
                throw new IllegalStateException("The bestEmployee (" + bestEmployee + ") cannot be null.");
            }
            shiftAssignment.setEmployee(bestEmployee);
            workingMemory.update(assignmentHandle, shiftAssignment);
            logger.debug("    ShiftAssignment ({}) initialized.", shiftAssignment);
        }

        Collections.sort(shiftAssignmentList, new PersistableIdComparator());
        nurseRoster.setShiftAssignmentList(shiftAssignmentList);
    }

    public List<ShiftAssignment> createAssignmentList(NurseRoster nurseRoster) {
        List<Shift> shiftList = nurseRoster.getShiftList();
        List<ShiftDate> shiftDateList = nurseRoster.getShiftDateList();

        List<ShiftInitializationWeight> shiftInitializationWeightList
                = new ArrayList<ShiftInitializationWeight>(shiftList.size());
        for (Shift shift : shiftList) {
            shiftInitializationWeightList.add(new ShiftInitializationWeight(nurseRoster, shift));
        }
        Collections.sort(shiftInitializationWeightList);

        List<ShiftAssignment> shiftAssignmentList = new ArrayList<ShiftAssignment>(
                shiftDateList.size() * nurseRoster.getEmployeeList().size());
        int assignmentId = 0;
        for (ShiftInitializationWeight shiftInitializationWeight : shiftInitializationWeightList) {
            Shift shift = shiftInitializationWeight.getShift();
            for (int i = 0; i < shift.getRequiredEmployeeSize(); i++) {
                ShiftAssignment shiftAssignment = new ShiftAssignment();
                shiftAssignment.setId((long) assignmentId);
                shiftAssignment.setShift(shift);
                shiftAssignmentList.add(shiftAssignment);
                assignmentId++;
            }
        }
        return shiftAssignmentList;
    }

    private static class ShiftInitializationWeight implements Comparable<ShiftInitializationWeight> {

        private Shift shift;

        private ShiftInitializationWeight(NurseRoster nurseRoster, Shift shift) {
            this.shift = shift;
        }

        public Shift getShift() {
            return shift;
        }

        public int compareTo(ShiftInitializationWeight other) {
            return new CompareToBuilder()
                    .append(shift.getShiftDate(), other.shift.getShiftDate()) // Ascending
                    .append(other.shift.getRequiredEmployeeSize(), shift.getRequiredEmployeeSize()) // Descending
                    .append(shift.getShiftType(), other.shift.getShiftType()) // Ascending
                    .toComparison();
        }

    }

}
