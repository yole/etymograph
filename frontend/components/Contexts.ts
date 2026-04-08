import {createContext} from "react";
import {AuthStatusViewModel, GraphViewModel, InputAssistViewModel, LanguageShortViewModel, RuleShortViewModel} from "@/models";

interface GlobalContextType {
    authStatus?: AuthStatusViewModel;
    graphs: GraphViewModel[];
    languages: LanguageShortViewModel[];
    inputAssists: InputAssistViewModel;
    rules: RuleShortViewModel[];
}

interface AuthContextType {
    authStatus?: AuthStatusViewModel;
}

export const GraphContext = createContext<string | undefined>(undefined)
export const GlobalStateContext = createContext<GlobalContextType | undefined>(undefined)
export const AuthContext = createContext<AuthContextType | undefined>(undefined)
