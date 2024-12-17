import {createContext} from "react";
import {GraphViewModel, LanguageShortViewModel, RuleShortViewModel} from "@/model";

type InputAssistGraphemeType = {
    text: string;
    languages: string[];
}

export type InputAssistType = {
    graphemes: InputAssistGraphemeType[];
}

interface GlobalContextType {
    graphs: GraphViewModel[];
    languages: LanguageShortViewModel[];
    inputAssists: InputAssistType;
    rules: RuleShortViewModel[];
}

export const GraphContext = createContext<string | undefined>(undefined)
export const GlobalStateContext = createContext<GlobalContextType | undefined>(undefined)
