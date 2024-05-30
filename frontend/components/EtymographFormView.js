import {createContext, useContext, useState} from "react";
import {allowEdit} from "@/api";

export const EditModeContext = createContext(undefined)
export const SetEditModeContext = createContext((value) => {})

export function View(props) {
    const editMode = useContext(EditModeContext)
    return !editMode ? props.children : <></>
}

export default function EtymographFormView(props) {
    const editButtonTitle = props.editButtonTitle ?? "Edit"
    const [editMode, setEditMode] = useState(false)

    return <EditModeContext.Provider value={editMode}>
        <SetEditModeContext.Provider value={setEditMode}>
            {props.children}
            {allowEdit() && !editMode && <button onClick={() => setEditMode(true)}>{editButtonTitle}</button>}
        </SetEditModeContext.Provider>
    </EditModeContext.Provider>
}
