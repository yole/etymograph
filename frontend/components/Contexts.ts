import {createContext} from "react";

type InputAssistGraphemeType = {
    text: string;
    languages: string[];
}

export type InputAssistType = {
    graphemes: InputAssistGraphemeType[];
}

type GlobalContextType = {
    inputAssists: InputAssistType;
}

export const GraphContext = createContext(undefined)
export const GlobalStateContext = createContext<GlobalContextType | undefined>(undefined)
