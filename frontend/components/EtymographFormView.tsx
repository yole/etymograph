import {useContext, useState} from "react";
import {useAllowEditGraph} from "@/api";
import {EtymographFormButton} from "@/components/EtymographForm";
import {EditModeContext, SetEditModeContext} from "@/components/EditModeContexts";

export function View(props) {
    const editMode = useContext(EditModeContext)
    return !editMode ? props.children : <></>
}

interface EtymographFormViewProps {
    editButtonTitle?: string;
    buttons?: EtymographFormButton[]
    children?: React.ReactNode;
}

export default function EtymographFormView(props: EtymographFormViewProps) {
    const editButtonTitle = props.editButtonTitle ?? "Edit"
    const [editMode, setEditMode] = useState(false)
    const canEdit = useAllowEditGraph()

    const buttons = props.buttons || []

    return <EditModeContext.Provider value={editMode}>
        <SetEditModeContext.Provider value={setEditMode}>
            {props.children}
            {canEdit && <>
                {!editMode && <button className="uiButton" onClick={() => setEditMode(true)}>{editButtonTitle}</button>}
                {buttons.map(b => <>{' '}<button type="button" className="uiButton" onClick={() => b.callback({})}>{b.text}</button></>)}
            </>}
        </SetEditModeContext.Provider>
    </EditModeContext.Provider>
}
