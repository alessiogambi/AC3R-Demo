package org.saarland.accidentconstructor;

import org.saarland.accidentelementmodel.RoadShape;
import org.saarland.accidentelementmodel.Street;
import org.saarland.accidentelementmodel.TestCaseInfo;
import org.saarland.nlptools.Stemmer;
import org.saarland.ontologyparser.AccidentConcept;
import org.saarland.ontologyparser.OntologyHandler;
import org.saarland.xmlmodules.XMLAccidentCaseParser;

import java.util.*;

public class EnvironmentAnalyzer {

    private Street previousStreet = null;

    private ArrayList<String> connectedStreetDirections;

    public Street getPreviousStreet() {
        return previousStreet;
    }

    public void setPreviousStreet(Street previousStreet) {
        this.previousStreet = previousStreet;
    }

    public EnvironmentAnalyzer(){ connectedStreetDirections = new ArrayList<String>();   }

    public void extractEnvironmentProp(LinkedList<LinkedList<String>> environmenTaggedWordsAndDependencies,
                                       OntologyHandler parser, TestCaseInfo testCase)
    {


        LinkedList<String> taggedWordList = environmenTaggedWordsAndDependencies.get(0);
        LinkedList<String> dependencyList = environmenTaggedWordsAndDependencies.get(1);
        LinkedList<String> processedConcepts = new LinkedList<String>();

        // Set multiple road reference in this sentence to be false by default
        boolean isMultipleRoad = false;

        HashMap<String, String> newTestCaseProp = testCase.getTestCaseProp();

        Street currentStreet = null;
        HashMap<String, String> currentStreetProp = null;

        // TODO: needs to define plural form of roads
        String[] pluralRoadWords = new String[]{"roadways", "streets", "roads"};

        // Detect whether we have multiple roadways

        for (int i = 0; i < taggedWordList.size(); i++)
        {
            String taggedWord = taggedWordList.get(i).toLowerCase();

            if (taggedWord.contains("t-intersection") || taggedWord.contains("t-junction"))
            {
                ConsoleLogger.print('d',"Found T-intersection");
                testCase.putValToKey("junction_type", "t-junction");
                // If there is only 1 road exists, make a new one
                if (testCase.getStreetList().size() == 1)
                {
                    testCase.createNewStreet();
                }
                else if (testCase.getStreetList().size() == 0)
                {
                    currentStreet = testCase.createNewStreet();
                    testCase.createNewStreet();
                }
                ConsoleLogger.print('d',"Current Street assigned in Intersection " + currentStreet.getStreetPropertyValue("road_ID"));
            }

            for (String pluralRoadWord : pluralRoadWords)
            {
                if (taggedWord.startsWith(pluralRoadWord))
                {
                    isMultipleRoad = true;
                    // Find the number of roads if specified
                    LinkedList<String> pluralRoadwayNumDep = AccidentConstructorUtil.findConnectedDependenciesWithKeyWord(
                            dependencyList, taggedWordList, pluralRoadWord, "", 100);

                    ConsoleLogger.print('d',"RoadNumDep size " + pluralRoadwayNumDep.size());

                    if (pluralRoadwayNumDep.size() == 1)
                    {
                        String[] roadWayNumWordPair =
                                AccidentConstructorUtil.getWordPairFromDependency(pluralRoadwayNumDep.get(0));

                        String numToken = AccidentConstructorUtil.getOtherWordInDep(pluralRoadWord, roadWayNumWordPair);

                        int roadNum = Integer.parseInt(AccidentConstructorUtil.getWordFromToken(numToken));

                        ConsoleLogger.print('d',"Roadnum is " + roadNum);

                        // Create new street based on road numbers
                        for (int r = 0; r < roadNum; r++)
                        {
                            testCase.createNewStreet();
                        }
                        break;
                    }
                }
            } // End looping through each plural road word

            if (isMultipleRoad)
            {
                break;
            }
        }

        ConsoleLogger.print('d',"Prev street ID before analyzing depList " + dependencyList + "\n " + (previousStreet == null ? "null" : previousStreet.getStreetPropertyValue("road_ID")));

        for (String dependency : dependencyList)
        {

            ConsoleLogger.print('d',"Processing " + dependency);
            String[] tokenPair = AccidentConstructorUtil.getWordPairFromDependency(dependency);

            String[] wordPair = {
                    AccidentConstructorUtil.getWordFromToken(tokenPair[0]),
                    AccidentConstructorUtil.getWordFromToken(tokenPair[1])
            };

            // Find whether a road is cut at t-intersection by finding nmod_in or nmod_on dependency that has occur word
            if (dependency.contains("nmod:")  || dependency.contains("occur"))
            {
                if (testCase.getTestCaseProp().get("junction_type").equals("t-junction"))
                {
                    String[] wordPairInDep = AccidentConstructorUtil.getWordPairFromDependency(dependency);
                    String otherWordInDep = AccidentConstructorUtil.getOtherWordInDep("occur", wordPairInDep);

                    LinkedList<String> directionDepsOfOtherWord = AccidentConstructorUtil.findConnectedDependencies(dependencyList,
                            taggedWordList, otherWordInDep, dependency, 0);

                    // Find dependency that contains direction
                    for (String directionDep : directionDepsOfOtherWord)
                    {
                        String[] directionWordPair = AccidentConstructorUtil.getWordPairFromDependency(directionDep);
                        String assumedDirectionWord = AccidentConstructorUtil.getWordFromToken(
                                AccidentConstructorUtil.getOtherWordInDep(otherWordInDep, directionWordPair));

                        AccidentConcept assumedDirectionConcept = parser.findExactConcept(assumedDirectionWord);
//                        ConsoleLogger.print('d',"AssumedDirectionWord is " + assumedDirectionWord + " concept: " + assumedDirectionConcept);
                        if (assumedDirectionConcept != null && assumedDirectionConcept.getConceptGroup().equals("vehicle_direction"))
                        {
                            connectedStreetDirections.add(AccidentConstructorUtil.convertDirectionWordToDirectionLetter(assumedDirectionWord));
                        }
                    }
                } // End checking t-junction in test case
            }


            // Extract the concept from the dependency, if applicable
            for (int i = 0; i < wordPair.length; i++) //word : wordPair)
            {
                String word = wordPair[i].toLowerCase();
                // Check if each word in the pair is already processed, the word must not be a number
                if (processedConcepts.indexOf(word) == -1
                        && !AccidentConstructorUtil.isNumeric(word) && parser.isExactConceptExist(word))
                {
                    AccidentConcept conceptOfWord = parser.findExactConcept(word);
                    if (conceptOfWord != null && conceptOfWord.getInputGroup().equals("environment_properties")
                            || conceptOfWord.getConceptGroup().equals("vehicle_direction"))
                    {
                        ConsoleLogger.print('d', String.format("Found envi concept : %s with group : %s\n", word, conceptOfWord.getConceptGroup()));

                        // Initiate the current working street

                        if (!isMultipleRoad && conceptOfWord.getCategory() != null
                                && conceptOfWord.getCategory().equals("road"))
                        {
                            // If there is no street, create a street and begin to set Property
                            if (testCase.getStreetList().size() == 0)
                            {
                                ConsoleLogger.print('d',"Make a new street");
                                currentStreet = testCase.createNewStreet();
                                previousStreet = currentStreet;
                                ConsoleLogger.print('d',"Prev street ID when create " + previousStreet.getStreetPropertyValue("road_ID"));
                            }
                            else // street(s) is already created
                            {
                                if (currentStreet == null)
                                {
                                    ConsoleLogger.print('d',"Process street with doubt " + conceptOfWord.getConceptGroup());
                                    // Determine whether we need to refer a street a not
                                    if (conceptOfWord.getConceptGroup().equals("road_type"))
                                    {
                                        String otherWordToken = AccidentConstructorUtil.getOtherWordInDep(word, wordPair);

                                        String otherWord = AccidentConstructorUtil.getWordFromToken(otherWordToken).toLowerCase();


                                        // If this is a east/west direction road, set road#1 as currentStreet
                                        if (otherWord.contains("east")
                                                || otherWord.contains("west")
                                                && !otherWord.contains("southeast") && !otherWord.contains("southwest")
                                                && !otherWord.contains("northeast") && !otherWord.contains("northwest"))
                                        {
                                            ConsoleLogger.print('d',"Find east/west street");
                                            // If the street #1 is already created, assign it as currentStreet
                                            if (testCase.getStreetList().get(0) != null)
                                            {
                                                currentStreet = testCase.getStreetList().get(0);
                                            }
                                            else
                                            {
                                                currentStreet = testCase.createNewStreet();
                                            }

                                            if (currentStreet != null)
                                            {

                                                String direction = AccidentConstructorUtil.convertDirectionWordToDirectionLetter(otherWord);

                                                currentStreet.putValToKey("road_navigation", direction);
                                                previousStreet = currentStreet;
                                            }
                                        }
                                        // If this is a north/south direction road, set road#2 as currentStreet
                                        else if (otherWord.contains("north")
                                                || otherWord.contains("south"))
                                        {
                                            ConsoleLogger.print('d',"Find north/south street");
                                            // If the street #2 is already created, assign it as currentStreet
                                            if (testCase.getStreetList().get(1) != null)
                                            {
                                                currentStreet = testCase.getStreetList().get(1);
                                            }
                                            else // create new street and assign to it
                                            {
                                                currentStreet = testCase.createNewStreet();
                                            }

                                            if (currentStreet != null)
                                            {

                                                currentStreet.putValToKey("road_navigation",
                                                        AccidentConstructorUtil.convertDirectionWordToDirectionLetter(otherWord));
                                                previousStreet = currentStreet;
                                            }
                                        }
                                        else // Find other street by looking at determinant
                                        {

                                            // Check whether the 2 words before road-type word is a determinant
                                            int roadWordPosition = AccidentConstructorUtil.getPositionFromToken(tokenPair[i]);
                                            ConsoleLogger.print('d',"roadWordPos " + roadWordPosition);
                                            if (roadWordPosition != -1) {
                                                String taggedWordToken = taggedWordList.get(roadWordPosition - 1);
                                                String wordType = AccidentConstructorUtil.getWordTypeFromPOSToken(taggedWordToken);
                                                String taggedWord = AccidentConstructorUtil.getWordFromToken(taggedWordToken);
                                                ConsoleLogger.print('d',"Word Type " + wordType);

                                                // There may be a wrong hit, need to scan +- 2 positions to find the road-type word
                                                if (!wordType.startsWith("NN") || (wordType.startsWith("NN")
                                                        && parser.isExactConceptExist(taggedWord)
                                                        && !parser.findExactConcept(taggedWord).equals("road_type"))) {
                                                    ConsoleLogger.print('d',"Checking Road Word Pos " + roadWordPosition);
                                                    for (int l = roadWordPosition - 2; l <= roadWordPosition + 2; l++) {
                                                        ConsoleLogger.print('d',"l is " + l);
                                                        taggedWordToken = taggedWordList.get(l);
                                                        taggedWord = AccidentConstructorUtil.getWordFromToken(taggedWordToken);

                                                        ConsoleLogger.print('d',"Checking Road Word " + taggedWord);

                                                        // Found it
                                                        if (parser.isExactConceptExist(taggedWord)
                                                                && parser.findExactConcept(taggedWord).equals("road_type")) {
                                                            roadWordPosition = l;
                                                            break;
                                                        }
                                                    }
                                                }

                                                // Scan for "the" determinant, if exists, take the last street in the street arr
                                                boolean previousStreetFound = false;
                                                for (int l = roadWordPosition - 2; l <= roadWordPosition && l < taggedWordList.size(); l++) {
                                                    taggedWordToken = taggedWordList.get(l);

                                                    ConsoleLogger.print('d',"Checking DT Word " + taggedWordToken);
                                                    if (taggedWordToken.endsWith("/DT")) {
                                                        String determinant = AccidentConstructorUtil.
                                                                getWordFromPOSToken(taggedWordToken).toLowerCase();
                                                        if (determinant.equals("the") || determinant.equals("this"))
                                                        {
                                                            int lastStreetIndex = testCase.getStreetList().size() - 1;
//                                                            currentStreet = testCase.getStreetList().get(lastStreetIndex);
                                                            currentStreet = previousStreet;
                                                            //previousStreet = currentStreet;
                                                            previousStreetFound = true;
                                                            ConsoleLogger.print('d',"get any street ? " + currentStreet.getStreetProp().get("road_ID"));
                                                            break;
                                                        }
                                                    }
                                                }

                                                if (currentStreet == null && !previousStreetFound && testCase.getStreetList().size() < 2) {
                                                    if (previousStreet == null)
                                                    {
                                                        currentStreet = testCase.createNewStreet();
                                                    }
                                                    else
                                                    {
                                                        currentStreet = previousStreet;
                                                    }
                                                    previousStreet = currentStreet;
                                                }

                                            } // End check if extract word index from token is successful
                                        } // End looking for current street by examining determinant
                                    } // End check if the word is of road-type concept
                                } // End checking if current street is null
                            } // End processing if a street is already created
                        } // End checking road-related concept for single street


                        if (currentStreet != null)
                        {
                            ConsoleLogger.print('d',"Current Street " + currentStreet.getStreetProp().get("road_ID"));
                            currentStreetProp = currentStreet.getStreetProp();
                        }


                        if ( conceptOfWord.getConceptGroup().equals("speed_limit"))
                        {
                            if (word.equals("speed_limit")) {
                                // Find the speed limit value
                                String speedMeasurementUnit = "mph";

                                LinkedList<String> speedLimitDeps = AccidentConstructorUtil.findConnectedDependencies(
                                        dependencyList, null, speedMeasurementUnit, dependency, 0);

                                // If speed limit is not recorded in mph, find the kmph version
                                if (speedLimitDeps.size() == 0) {
                                    speedMeasurementUnit = "kmph";
                                    speedLimitDeps = AccidentConstructorUtil.findConnectedDependencies(
                                            dependencyList, null, speedMeasurementUnit, dependency, 0);
                                }

                                for (String speedLimitDependency : speedLimitDeps) {
                                    String assumedLimitNumber = AccidentConstructorUtil.getOtherWordInDep(
                                            speedMeasurementUnit, AccidentConstructorUtil.getWordPairFromDependency(speedLimitDependency));


                                    assumedLimitNumber = AccidentConstructorUtil.getWordFromToken(assumedLimitNumber);

                                    if (assumedLimitNumber.matches("\\d*")) {
                                        ConsoleLogger.print('d',"Found speed limit number " + assumedLimitNumber + " isMultiRoad " + isMultipleRoad);
                                        // convert kmph to mph for BeamNG
                                        if (speedMeasurementUnit.equals("kmph")) {
                                            speedMeasurementUnit = "mph";
                                            assumedLimitNumber = "" + Double.parseDouble(assumedLimitNumber) * 0.621427;
                                        }
//                                        newTestCaseProp.put("speed_limit", assumedLimitNumber + " " + word);
//                                    currentStreet.putValToKey("speed_limit", assumedLimitNumber);

                                        putRoadPropToTargetRoads(isMultipleRoad, currentStreet, "speed_limit"
                                                , assumedLimitNumber, testCase);
                                        break;
                                    }

                                }
                            }
                        }
                        else if (conceptOfWord.getConceptGroup().equals("road_grade"))
                        {
                            // Find the grade percentage
                            LinkedList<String> gradeDeps = AccidentConstructorUtil.findConnectedDependencies(
                                    dependencyList, null, "%", dependency, 0);

                            String gradeUnit = "%";

                            ConsoleLogger.print('d',"Grade deps size before " + gradeDeps.size());

                            if (gradeDeps.size() == 0)
                            {
                                gradeDeps = AccidentConstructorUtil.findConnectedDependencies(
                                        dependencyList, null, "percent", dependency, 0);

                                gradeUnit = "percent";
                            }

                            ConsoleLogger.print('d',"Grade deps size after " + gradeDeps.size());

                            if (gradeDeps.size() > 0)
                            {
                                for (String gradeDependency : gradeDeps)
                                {
                                    String assumedNumber = AccidentConstructorUtil.getOtherWordInDep(
                                            gradeUnit, AccidentConstructorUtil.getWordPairFromDependency(gradeDependency));

                                    assumedNumber = AccidentConstructorUtil.getWordFromToken(assumedNumber);

                                    ConsoleLogger.print('d', "");

//                                    if (assumedNumber.matches("(\\d*)|(\\d*\\.\\d*)|(-\\d*.)"))
                                    if (AccidentConstructorUtil.isNumeric(assumedNumber))
                                    {

                                        if (gradeUnit.contains("percent"))
                                        {
                                            gradeUnit = "%";
                                        }

                                        // If this is a "grade" word, find whether it is up or downhill by the grade deg
                                        if (word.equals("grade"))
                                        {
                                            double gradeDegree = Double.parseDouble(assumedNumber);
                                            if (gradeDegree > 0)
                                            {
                                                ConsoleLogger.print('d',"Infer uphill grade");
                                                word = "uphill";
                                            }
                                            else if (gradeDegree < 0)
                                            {
                                                ConsoleLogger.print('d',"Infer downhill grade");
                                                word = "downhill";
                                            }
                                            else
                                            {
                                                ConsoleLogger.print('d',"Infer level grade");
                                                word = "level";
                                            }
                                        }

                                        ConsoleLogger.print('d',"Found grade " + assumedNumber);
//                                        newTestCaseProp.put("road_grade_deg", assumedNumber + gradeUnit);

//                                        newTestCaseProp.put("road_grade", word);
//                                        currentStreet.putValToKey("road_grade_deg",
//                                                "" + (Double.parseDouble(assumedNumber)));
//                                        currentStreet.putValToKey("road_grade", word);
                                        putRoadPropToTargetRoads(isMultipleRoad, currentStreet, "road_grade_deg"
                                                , "" + (Double.parseDouble(assumedNumber)), testCase);
                                        putRoadPropToTargetRoads(isMultipleRoad, currentStreet, "road_grade"
                                                , word, testCase);
                                        break;
                                    }
                                }
                            }

                        }
                        else if (conceptOfWord.getConceptGroup().equals("lane"))
                        {
                            // Find the lane number
                            if (word.equals("lane"))
                            {
                                String otherWordToken = AccidentConstructorUtil.getOtherWordInDep(word, wordPair);
                                String otherWord = AccidentConstructorUtil.getWordFromToken(otherWordToken);

                                if (AccidentConstructorUtil.isNumeric(otherWord))
                                {
                                    putRoadPropToTargetRoads(isMultipleRoad, currentStreet, "lane_num"
                                            , otherWord, testCase);
                                }
                            }
                            else if (word.matches("\\d-lane"))
                            {
                                ConsoleLogger.print('d',"Find specific lane number " + word.split("-")[0]);
                                putRoadPropToTargetRoads(false, currentStreet, "lane_num"
                                        , word.split("-")[0], testCase);
                            }
                        }
                        else if (conceptOfWord.getConceptGroup().equals("road_shape"))
                        {
                            ConsoleLogger.print('d',"analyze Road Shape for word " + word);
                            if (isMultipleRoad)
                            {
                                for (Street street : testCase.getStreetList())
                                {
                                    analyzeRoadShape(word, testCase, dependencyList, parser, street);
                                }
                            }
                            else
                            {
                                analyzeRoadShape(word, testCase, dependencyList, parser, currentStreet);
                            }
//                            ConsoleLogger.print('d',"Curr Street After analyze ");
//                            currentStreet.printStreetInfo();
                        }
                        else if (conceptOfWord.getConceptGroup().equals("road_type"))
                        {
                            putRoadPropToTargetRoads(isMultipleRoad, currentStreet, "road_type"
                                    , word, testCase);
                        }
                        else if (conceptOfWord.getConceptGroup().equals("junction_type"))
                        {
                            ConsoleLogger.print('d',"Junction type " + word);
                            // For T-intersection, create 2 roads

                        }
                        else if (conceptOfWord.getConceptGroup().equals("pavement_type"))
                        {
                            ConsoleLogger.print('d',"Pavement type " + word);
                            // For T-intersection, create 2 roads
                            putRoadPropToTargetRoads(isMultipleRoad, currentStreet, "pavement_type"
                                    , word, testCase);
                        }
                        else if (conceptOfWord.getConceptGroup().equals("traffic_sign"))
                        {
                            ConsoleLogger.print('d',"traffic sign found " + word);
                            appendRoadPropToTargetRoads(isMultipleRoad, currentStreet, "traffic_sign_list"
                                    , word, testCase);
                            // For T-intersection, create 2 roads

                        }
                        else if (conceptOfWord.getConceptGroup().equals("vehicle_direction"))
                        {
                            ConsoleLogger.print('d',"Found direction " + word);
                            String direction = AccidentConstructorUtil.convertDirectionWordToDirectionLetter(word);
                            // If the street does not have navigation, define it
                            if (currentStreet != null &&
                                    currentStreet.getStreetPropertyValue("road_navigation").equals(""))
                            {
                                putRoadPropToTargetRoads(false, currentStreet, "road_navigation", direction, testCase);
                                currentStreet.putValToKey("road_angle", "" + fuzzAngleOfRoad(direction));
                            } // End checking if navigation is ""

                            // If there is no specified street, find the street with empty navigation
                            else if (currentStreet == null)
                            {
                                Street noDirectionStreet = null;
                                boolean allStreetHasNoDirection = true;
                                int setDirectionInRoadID = -1;
                                for (Street street : testCase.getStreetList())
                                {
                                    if (!street.getStreetPropertyValue("road_navigation").equals(""))
                                    {
                                        // Skip right away if this direction matches with some other direction
                                        if (street.getStreetPropertyValue("road_navigation").equals(direction))
                                        {
                                            continue;
                                        }
                                        allStreetHasNoDirection = false;
                                    }
                                    else
                                    {

                                        noDirectionStreet = street;

                                    }
                                }

                                // If there is a no direction street, while the other street has a non-matching direction,
                                // assign this road direction to it
                                if (noDirectionStreet != null && !allStreetHasNoDirection)
                                {
                                    putRoadPropToTargetRoads(isMultipleRoad, noDirectionStreet, "road_navigation",
                                            direction, testCase);
                                    noDirectionStreet.putValToKey("road_angle", "" + fuzzAngleOfRoad(direction));
                                }

                            } // End checking currentStreet is null
                        }
                        else
                        {
                            if (newTestCaseProp.containsKey(conceptOfWord.getConceptGroup())) {
                                newTestCaseProp.put(conceptOfWord.getConceptGroup(), word);
                                putRoadPropToTargetRoads(isMultipleRoad, currentStreet, conceptOfWord.getConceptGroup()
                                        , word, testCase);
                                processedConcepts.add(word);
                            }


                        }


                    } // End extracting environment properties
                    //currentStreet.setStreetProp(currentStreetProp);

                } // End checking if the word is already processed

                // Find the exact environment concept
                // Stem the word and find the exact concept
                Stemmer stemmer = new Stemmer();
                String stemmedWord = stemmer.stem(word);

                AccidentConcept exactWordConcept = parser.findExactConcept(stemmedWord);

                // If concept exists, add the value to the corresponding test case attr
                if (exactWordConcept != null)
                {
                    ConsoleLogger.print('d',"Exact concept of word " + stemmedWord + " is " + exactWordConcept.getConceptGroup());
                    if (exactWordConcept.getConceptGroup().equals("lighting") || exactWordConcept.getConceptGroup().equals("weather"))
                    {
                        String conceptGroup = exactWordConcept.getConceptGroup();
                        ConsoleLogger.print('d',"Find Lighting or Weather word " + word);
                        String currentLightingWeatherCondition = testCase.getEnvPropertyValue(conceptGroup);

                        // Find the negation of this word
                        boolean hasNegation = AccidentConstructorUtil.findNegationOfToken(word, dependencyList);

                        if (hasNegation)
                        {
                            ConsoleLogger.print('d',"Has negation of env term " + stemmedWord);
                            stemmedWord = "n_" + stemmedWord;
                        }

                        if (currentLightingWeatherCondition.equals("normal"))
                        {
                            testCase.putValToKey(conceptGroup, stemmedWord);
                        }
                        else
                        {
                            if (!currentLightingWeatherCondition.contains(stemmedWord))
                            {
                                testCase.putValToKey(conceptGroup, currentLightingWeatherCondition + " " + stemmedWord);
                            }
                        }

                    } // End process lighting
                    else
                    {
                        testCase.putValToKey(exactWordConcept.getConceptGroup(), stemmedWord);
                    }
                } // End checking exact concept is not null

                // Extract Misc descriptions
                if (dependency.startsWith("nmod:of"))
                {

                    AccidentConcept wordConcept = parser.findExactConcept(word);

                    // Find whether street has parking line
                    if (wordConcept != null
                            && wordConcept.getConceptGroup().equals("road_type")
                            && dependency.contains("side-"))
                    {
                        String sidePattern = "side-";
                        LinkedList<String> relatedSideDependencies = AccidentConstructorUtil.findConnectedDependencies(dependencyList, taggedWordList,
                                sidePattern, dependency, 0);


                        // Check if side dependency has line for parking
                        for (String sideDependency : relatedSideDependencies)
                        {
                            ConsoleLogger.print('d',"Side related Dep " + sideDependency);
                            String[] sideDepWordPair = AccidentConstructorUtil.getWordPairFromDependency(sideDependency);

                            String otherWord = AccidentConstructorUtil.getOtherWordInDep(sidePattern, sideDepWordPair);

                            // Check if the parking line is filled with cars
                            if (otherWord.contains("line"))
                            {
                                LinkedList<String> carLineDeps = AccidentConstructorUtil.findConnectedDependencies(
                                        dependencyList, taggedWordList,
                                        otherWord, sideDependency, 0);

                                ConsoleLogger.print('d',"Other word " + otherWord + " Car line Deps size " + carLineDeps.size());

                                // No info about the objects on the parking line
                                if (carLineDeps.size() == 0)
                                {
//                                    newTestCaseProp.put("road_park_line_fill", "0");
                                    currentStreet.putValToKey("road_park_line_fill", "0");
                                }

                                for (String carDependency : carLineDeps)
                                {
                                    // Found that the line is filled with cars
                                    if (carDependency.contains("car"))
                                    {
                                        ConsoleLogger.print('d',"find line filled with cars");
//                                        newTestCaseProp.put("road_park_line_fill", "1");
                                        currentStreet.putValToKey("road_park_line_fill", "1");

                                        // If the parking line is not found yet, set both sides have a parking line
                                        if (newTestCaseProp.get("road_park_line").equals(""))
                                        {
//                                            newTestCaseProp.put("road_park_line", "3");
                                            currentStreet.putValToKey("road_park_line", "3");
                                        }
                                    }
                                }
                            } // End checking if parking line is filled with cars
                            // Detect left parking line
                            else if (otherWord.contains("(left-"))
                            {
                                if (newTestCaseProp.get("road_park_line").equals("")
                                        || newTestCaseProp.get("road_park_line").equals("3"))
                                {
//                                    newTestCaseProp.put("road_park_line", "1");
                                    currentStreet.putValToKey("road_park_line", "1");
                                }
                                // If somehow people specify "left and right", the set the lane has both parking lines
                                else if (newTestCaseProp.get("road_park_line").equals("2"))
                                {
//                                    newTestCaseProp.put("road_park_line", "3");
                                    currentStreet.putValToKey("road_park_line", "3");
                                }

                            }
                            // Detect right parking line
                            else if (otherWord.contains("(right-"))
                            {
                                if (newTestCaseProp.get("road_park_line").equals("")
                                        || newTestCaseProp.get("road_park_line").equals("3"))
                                {
//                                    newTestCaseProp.put("road_park_line", "2");
                                    currentStreet.putValToKey("road_park_line", "2");
                                }
                                // If somehow people specify "left and right", the set the lane has both parking lines
                                else if (newTestCaseProp.get("road_park_line").equals("1"))
                                {
//                                    newTestCaseProp.put("road_park_line", "3");
                                    currentStreet.putValToKey("road_park_line", "3");
                                }
                            }

                        } // End processing line dependency
                    } // End checking road has parking line
                    if (currentStreetProp != null)
                    {
                        currentStreet.printStreetInfo();
                        currentStreet.setStreetProp(currentStreetProp);
                    }
                } // End processing nmod:of dep


            } // End processing each word

            if (currentStreetProp != null)
            {
                ConsoleLogger.print('d',"Street Info ");
                currentStreet.printStreetInfo();
                currentStreet.setStreetProp(currentStreetProp);
            }

            testCase.setTestCaseProp(newTestCaseProp);
        } // End processing each dep

    }

    public void analyzeRoadShape(String roadShapeString, TestCaseInfo testCase, LinkedList<String> dependencyList,
                                 OntologyHandler parser, Street currentStreet)
    {

        HashMap<String, String> newTestCaseProp = testCase.getTestCaseProp();
        if (roadShapeString.equalsIgnoreCase("curve"))
        {
//            newTestCaseProp.put("road_shape", RoadShape.C_CURVE);
            currentStreet.putValToKey("road_shape", RoadShape.C_CURVE);

            LinkedList<String> curveRelatedDependencies = AccidentConstructorUtil.findConnectedDependencies(
                    dependencyList, null, roadShapeString, "", 0);

            // TODO: concat all the dependencies, then find the radius value
            AccidentConcept curveConcept = parser.findExactConcept(roadShapeString);

            HashMap<String, String> dataProps = curveConcept.getDataProperties();
            Iterator it = dataProps.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, String> pair = (Map.Entry)it.next();
                ConsoleLogger.print('d',"Key = Pair : " + pair.getKey() + " = " + pair.getValue());
                String roadShapeAttrValue = pair.getValue();
                String roadShapeAttr   = pair.getKey();

                // Check if this attribute has pattern or not
                if (roadShapeAttrValue.startsWith("pattern"))
                {
                    LinkedList<String> attributeDependencies = AccidentConstructorUtil.findConnectedDependencies(
                            dependencyList, null, roadShapeAttr, "", 0);

                    // Get the pattern(s)
                    String[] patterns = roadShapeAttrValue.split(":")[1].split("\\|");

                    // Loop through the dependency and extract the correct pattern value
                    for (String attributeDependency : attributeDependencies)
                    {
                        String[] attrWordPair = AccidentConstructorUtil.getWordPairFromDependency(attributeDependency);

                        String assumedAttrWord = AccidentConstructorUtil.getWordFromToken(
                                AccidentConstructorUtil.getOtherWordInDep(roadShapeAttr, attrWordPair));

                        String roadShapeAndKey = roadShapeString + "_" + roadShapeAttr;

                        // Find the word that match the pattern, provided that the dependency contains the key String
                        for (String pattern : patterns)
                        {
//                            pattern = pattern.replace("\\", "\\\\");
                            ConsoleLogger.print('d',"Pattern " + pattern + " value " + assumedAttrWord);

//                            ConsoleLogger.print('d',"prev street ID is " + previousStreet.getStreetPropertyValue("road_ID"));
//                            ConsoleLogger.print('d',"curr street ID is" + currentStreet.getStreetPropertyValue("road_ID"));

                            if (assumedAttrWord.matches(pattern))
                            {
                                ConsoleLogger.print('d',"Found match in dep:" + attributeDependency);

                                if (currentStreet.getStreetPropertyValue(pair.getKey()).equals(""))
                                {
                                    // This is a radius in meter, remove m and no conversion is needed
                                    if (assumedAttrWord.endsWith("m"))
                                    {
                                        assumedAttrWord = assumedAttrWord.replace("m", "");
//                                        if (currentStreet.getStreetPropertyValue("curve_direction").equals("left"))
//                                        {
//                                            assumedAttrWord = "-" + assumedAttrWord;
//                                        }
                                    }
//                                    newTestCaseProp.put(roadShapeAndKey, assumedAttrWord);
                                    currentStreet.putValToKey(roadShapeAndKey, assumedAttrWord);
                                    // If this specify a radius, set it as curve bow shape
//                                    newTestCaseProp.put("road_shape", RoadShape.BOW_CURVE);
                                    currentStreet.putValToKey("road_shape", RoadShape.BOW_CURVE);

//                                    testCase.setTestCaseProp(newTestCaseProp);
                                    break;
                                }

                            }

                        }

                    }
                }
                else // Check other attributes that is not a pattern
                {
                    // If there are many possible attributes, search for each of them in the dependency list
                    if (roadShapeAttrValue.contains("|"))
                    {
                        String[] allValues = roadShapeAttrValue.split("\\|");

                        for (String attrVal : allValues)
                        {
                            ConsoleLogger.print('d',"AttrVal : " + attrVal);
                            LinkedList<String> relatedAttrValDep = AccidentConstructorUtil.findConnectedDependencies(
                                    curveRelatedDependencies, null, attrVal, "", 0);



                            // Slight curve


                            // Check if there is only 1 value, if not, make sure that only 1 dep containing both road
                            // shape and attrVal exists
                            if (relatedAttrValDep.size() > 1)
                            {
                                for (String dependency : relatedAttrValDep)
                                {
                                    if (dependency.contains(attrVal) && dependency.contains(roadShapeString))
                                    {
                                        // Left curve, set radius to -1 * radius
                                        if (attrVal.equals("left"))
                                        {
                                            ConsoleLogger.print('d',"Set radius to negative value for left curve");
                                            currentStreet.putValToKey("curve_radius",
                                                    (Double.parseDouble(currentStreet.getStreetPropertyValue("curve_radius")) * -1) + "");
                                        }

                                        relatedAttrValDep.removeAll(relatedAttrValDep);
                                        relatedAttrValDep.add(dependency);
                                    }
                                }
                            }

                            // Found the attribute, add the property to the test case
                            if (relatedAttrValDep.size() == 1)
                            {
                                if (newTestCaseProp.containsKey(roadShapeAttr))
                                {
//                                    newTestCaseProp.put(roadShapeAttr, attrVal);
//                                    testCase.setTestCaseProp(newTestCaseProp);
                                    currentStreet.putValToKey(roadShapeAttr, attrVal);
                                    break;
                                }
                            }
                        } // End looping through the value of attr
                    } // End checking multiple values exists
                } // End check the attr that does not contain pattern

                it.remove(); // avoids a ConcurrentModificationException
            } // End looping through dataprop key-value pair
        } // End analyze curve
        else
        {
            if (roadShapeString.equalsIgnoreCase("straight"))
            {
                ConsoleLogger.print('d',"Found straight road shape!");
//                newTestCaseProp.put("road_shape", RoadShape.STRAIGHT);
//                testCase.setTestCaseProp(newTestCaseProp);
                currentStreet.putValToKey("road_shape", RoadShape.STRAIGHT);
                currentStreet.putValToKey("curve_radius", "0");
            }
        }
    }



    public void checkMissingEnvironmentProperties(TestCaseInfo testCase)
    {
        HashMap<String, String> testCaseProp = testCase.getTestCaseProp();
        HashMap<String, String> streetProp = new HashMap<String, String>();
        ArrayList<Street> streetList = testCase.getStreetList();

        for (int i = 0; i < streetList.size(); i++)
        {
            streetProp = streetList.get(i).getStreetProp();
            ConsoleLogger.print('d',"Check Missing property for car #" + streetProp.get("road_ID"));
            // Try to assign assumed value to an important empty tag, or find the value in the file
            // Negate the curve radius if the curve direction is right


            try {
                for (String key : streetProp.keySet()) {
                    if (streetProp.get(key).equals("") || streetProp.get(key) == null) {
                        // Find the Number of Lane tag

                        if (key.equals("lane_num")) {
                            ConsoleLogger.print('d',"Processing Missing Lane Number");
                            try {
                                String laneNumber = XMLAccidentCaseParser.readTagOfAGivenOrder("ROADWAY",
                                        "NUM_OF_TRAVEL_LANES", Integer.parseInt(streetProp.get("road_ID")) - 1);
                                ConsoleLogger.print('d',"Lane Number in Tag NUM_OF_TRAVEL_LANES for road #" + streetProp.get("road_ID") + " is " + laneNumber);
                                laneNumber = AccidentConstructorUtil.transformWordNumIntoNum(laneNumber.toLowerCase() + " ").trim();
//                                testCaseProp.put(key, laneNumber);
                                streetProp.put(key, laneNumber);
                            }
                            catch (Exception ex)
                            {
                                streetProp.put(key, "2");
                                ConsoleLogger.print('d',"Error in reading lane number!!!! \n" + ex);
                            }
                        } // End finding value for lane_num
                        else if (key.equals("road_grade"))
                        {
                            // Assume a level road
                            streetProp.put(key, "level");
                            streetProp.put("road_grade_deg", "0");
                        }
                        else if (key.equals("road_shape"))
                        {
                            // Assume a straight road
                            streetProp.put(key, RoadShape.STRAIGHT);
                            streetProp.put("curve_direction", "none");
                            streetProp.put("curve_radius", "0");
                        }
                        else if (key.equals("road_type"))
                        {
                            streetProp.put(key, "roadway");
                        }
                        else if (key.equals("road_material"))
                        {
                            String material = XMLAccidentCaseParser.readTagOfAGivenOrder("ROADWAY",
                                    "SURFACE_TYPE", Integer.parseInt(streetProp.get("road_ID")) - 1);
                            if (material.contains("asphalt") || material == null)
                            {
                                material = "asphalt";
                            }
                            streetProp.put(key, material);
                        }
                        else if (key.equals("speed_limit"))
                        {
                            String speed_limit = XMLAccidentCaseParser.readTagOfAGivenOrder("ROADWAY",
                                    "SPEED_LIMIT", Integer.parseInt(streetProp.get("road_ID")) - 1);
                            streetProp.put(key, "" + Math.round(Double.parseDouble(speed_limit) * 0.621427));
                        }


                    } // End processing empty prop
                } // End looping through street keys

                // If we have t-intersection or roundabout, then set the cut road piece
                if (testCase.getTestCaseProp().get("junction_type").equals("t-junction"))
                {
                    for (String connectedStreetDirection : connectedStreetDirections)
                    {
                        // If the road has different connectedStreetDirection, it is a cut road
                        if (!streetProp.get("road_navigation").equals(connectedStreetDirection))
                        {
                            ConsoleLogger.print('d',"Find cut road #" + streetProp.get("road_ID"));
                            streetProp.put("is_single_road_piece", "T");
                        }
                    }

                } // End checking cut road for t-intersection
            }
            catch (Exception ex)
            {
                ConsoleLogger.print('e',"Exception in checking empty street prop \n" + ex);
            }
        }


    }

    public void analyzeRoadProperties()
    {

    }

    private boolean putRoadPropToTargetRoads(boolean isMultipleStreet, Street currentStreet,
                                          String propKey, String propVal, TestCaseInfo testCaseInfo)
    {
        try {
            // If all street is specified, set the prop to all streets
            if (isMultipleStreet)
            {
                for (Street street : testCaseInfo.getStreetList())
                {
                    street.putValToKey(propKey, propVal);
                }
            }
            else // Set prop to a particular street only
            {
                currentStreet.putValToKey(propKey, propVal);
            }
        }
        catch (Exception ex)
        {
            ConsoleLogger.print('e',"Error in put a prop to all roads!!!! \n" + ex);
            return false;
        }
        return true;
    }

    private boolean appendRoadPropToTargetRoads(boolean isMultipleStreet, Street currentStreet,
                                             String propKey, String propVal, TestCaseInfo testCaseInfo)
    {
        try {
            // If all street is specified, set the prop to all streets
            if (isMultipleStreet)
            {
                for (Street street : testCaseInfo.getStreetList())
                {
                    // If the concept already recorded, don't append it
                    if (!currentStreet.getStreetPropertyValue(propKey).contains(propVal))
                        street.putValToKey(propKey, currentStreet.getStreetPropertyValue(propKey) + ";" + propVal);
                }
            }
            else // Set prop to a particular street only
            {
                // If the concept already recorded, don't append it
                if (!currentStreet.getStreetPropertyValue(propKey).contains(propVal))
                    currentStreet.putValToKey(propKey, currentStreet.getStreetPropertyValue(propKey) + ";" + propVal);
            }
        }
        catch (Exception ex)
        {
            ConsoleLogger.print('e',"Error in put a prop to all roads!!!! \n" + ex);
            return false;
        }
        return true;
    }

    // Take the north as standard (0 degree), then measure angle clockwise i.e east is
    private int fuzzAngleOfRoad(String direction)
    {
        int angle = 0;


        if (direction.equals("NE"))
        {
            angle = fuzzWithinRange(210, 240);
        }
        else if (direction.equals("SE"))
        {
            angle = fuzzWithinRange(300, 330);
        }
        else if (direction.equals("SW"))
        {
            angle = fuzzWithinRange(30, 60);
        }
        else if (direction.equals("NW"))
        {
            angle = fuzzWithinRange(120, 150);
        }
        else if (direction.equals("N"))
        {
            angle = 180;
        }
        else if (direction.equals("S"))
        {
            angle = 0;
        }
        else if (direction.equals("E"))
        {
            angle = 270;
        }
        else if (direction.equals("W"))
        {
            angle = 90;
        }

        ConsoleLogger.print('d',"Fuzz Angle for direction  " + direction + " angle is " + angle);

        return angle;
    }

    private int fuzzWithinRange(int min, int max)
    {
        Random angleFuzzer = new Random();
        int angle = 0;

        for (int i = 0; i < 1000; i++)
        {
            int angleVal = angleFuzzer.nextInt(max);
            if (angleVal >= min)
            {
                angle = angleVal;
                break;
            }
        }
        return angle;
    }

}
