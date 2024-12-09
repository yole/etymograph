import {useContext, useState} from "react";
import {GlobalStateContext} from "@/components/Contexts";
import {FontAwesomeIcon} from '@fortawesome/react-fontawesome'
import {faKeyboard} from '@fortawesome/free-solid-svg-icons'
import {useFormContext} from "react-hook-form";

export default function InputAssist(props) {
    const {watch} = useFormContext()
    const globalState = useContext(GlobalStateContext)
    const [inputAssistVisible, setInputAssistVisible] = useState(false)

    if (!globalState.inputAssists) return <></>

    const lang = props.languageProp !== undefined
        ? watch(props.languageProp)
        : props.language

    function collectInputAssists(assists) {
        return assists.graphemes
            .filter((g) => !lang || g.languages.includes(lang))
            .map(g => g.text)
    }

    function handleInputAssist(id, char) {
        const inputField = document.getElementById(id)
        const currentValue = inputField.value
        const selectionEnd = inputField.selectionEnd;
        inputField.value = currentValue.substring(0, inputField.selectionStart) + char + currentValue.substring(selectionEnd)
        inputField.setSelectionRange(selectionEnd + 1, selectionEnd + 1)
        inputField.focus()
    }

    return <span className={props.inline ? "inputAssist" : ""}>
        <span className="iconWithMarginRight"><FontAwesomeIcon icon={faKeyboard} onClick={() => setInputAssistVisible(!inputAssistVisible)}/></span>
        {inputAssistVisible && collectInputAssists(globalState.inputAssists).map(assist =>
            <button type="button" className="inlineButton inputAssistButton"
                    onClick={() => handleInputAssist(props.id, assist) }
            >{assist}</button>
        )}
    </span>
}