import {createContext} from "react";
import {GraphViewModel, InputAssistViewModel, LanguageShortViewModel, RuleShortViewModel} from "@/models";

interface GlobalContextType {
    graphs: GraphViewModel[];
    languages: LanguageShortViewModel[];
    inputAssists: InputAssistViewModel;
    rules: RuleShortViewModel[];
}

export const GraphContext = createContext<string | undefined>(undefined)
export const GlobalStateContext = createContext<GlobalContextType | undefined>(undefined)
