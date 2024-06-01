import {useContext} from "react";
import {GlobalStateContext} from "@/components/Contexts";
import {useFormContext} from "react-hook-form";

export default function InputAssist(props) {
    const globalState = useContext(GlobalStateContext)
    const {setValue} = useFormContext()

    if (!globalState.inputAssists) return <></>

    function collectInputAssists(assists) {
        return assists.graphemes.map(g => g.text)
    }

    function handleInputAssist(id, char) {
        const inputField = document.getElementById(id)
        const currentValue = inputField.value
        const newValue = currentValue.substring(0, inputField.selectionStart) + char + currentValue.substring(inputField.selectionEnd)
        setValue(id, newValue)
        inputField.focus()
    }

    return <span className="inputAssist">
        {collectInputAssists(globalState.inputAssists).map(assist =>
            <button type="button" className="inlineButton inputAssistButton"
                    onClick={() => handleInputAssist(props.id, assist) }
            >{assist}</button>
        )}
    </span>
}