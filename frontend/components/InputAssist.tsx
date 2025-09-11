import {useContext, useState} from "react";
import {GlobalStateContext} from "@/components/Contexts";
import {FontAwesomeIcon} from '@fortawesome/react-fontawesome'
import {faKeyboard} from '@fortawesome/free-solid-svg-icons'
import {useFormContext} from "react-hook-form";
import {InputAssistViewModel} from "@/models";

interface InputAssistProps {
    languageProp?: string;
    language?: string;
    inline?: boolean;
    id: string;
}

export default function InputAssist(props: InputAssistProps) {
    const {watch} = useFormContext()
    const globalState = useContext(GlobalStateContext)
    const [inputAssistVisible, setInputAssistVisible] = useState(false)

    if (!globalState.inputAssists) return <></>

    const lang = props.languageProp !== undefined
        ? watch(props.languageProp)
        : props.language

    function collectInputAssists(assists: InputAssistViewModel): string[] {
        return assists.graphemes
            .filter((g) => !lang || g.languages.includes(lang))
            .map(g => g.text)
    }

    function handleInputAssist(id, char) {
        const inputField = document.getElementById(id) as HTMLInputElement
        const currentValue = inputField.value
        const selectionEnd = inputField.selectionEnd;
        inputField.value = currentValue.substring(0, inputField.selectionStart) + char + currentValue.substring(selectionEnd)
        inputField.setSelectionRange(selectionEnd + char.length, selectionEnd + char.length)
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