import {createContext} from "react";

export const EditModeContext = createContext<boolean | undefined>(undefined)
export const SetEditModeContext = createContext<((newState: boolean) => void) | undefined>(undefined)
