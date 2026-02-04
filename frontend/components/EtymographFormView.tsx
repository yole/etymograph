import {createContext, useContext, useState} from "react";
import {allowEdit} from "@/api";

export const EditModeContext = createContext<boolean | undefined>(undefined)
export const SetEditModeContext = createContext<((newState: boolean) => void) | undefined>(undefined)

export function View(props) {
    const editMode = useContext(EditModeContext)
    return !editMode ? props.children : <></>
}

interface EtymographFormViewProps {
    editButtonTitle?: string;
    children?: React.ReactNode;
}

export default function EtymographFormView(props: EtymographFormViewProps) {
    const editButtonTitle = props.editButtonTitle ?? "Edit"
    const [editMode, setEditMode] = useState(false)

    return <EditModeContext.Provider value={editMode}>
        <SetEditModeContext.Provider value={setEditMode}>
            {props.children}
            {allowEdit() && !editMode && <button className="uiButton" onClick={() => setEditMode(true)}>{editButtonTitle}</button>}
        </SetEditModeContext.Provider>
    </EditModeContext.Provider>
}
