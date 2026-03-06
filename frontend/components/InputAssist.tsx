import {useContext, useState} from "react";
import {GlobalStateContext} from "@/components/Contexts";
import {FontAwesomeIcon} from '@fortawesome/react-fontawesome'
import {faKeyboard} from '@fortawesome/free-solid-svg-icons'
import {InputAssistViewModel} from "@/models";
import {useEtymographFormContext} from "@/components/EtymographForm";

interface InputAssistProps {
    languageProp?: string;
    language?: string;
    inline?: boolean;
    id: string;
}

export default function InputAssist(props: InputAssistProps) {
    const form = useEtymographFormContext()
    const formValues = form.getValues()
    const globalState = useContext(GlobalStateContext)
    const [inputAssistVisible, setInputAssistVisible] = useState(false)

    if (!globalState.inputAssists) return <></>

    const lang = props.languageProp !== undefined
        ? formValues[props.languageProp]
        : props.language

    function collectInputAssists(assists: InputAssistViewModel): string[] {
        return assists.graphemes
            .filter((g) => !lang || g.languages.includes(lang))
            .map(g => g.text)
    }

    function handleInputAssist(id: string, char: string) {
        const inputField = document.getElementById(id) as HTMLInputElement | HTMLTextAreaElement | null
        if (inputField === null) return
        const selectionStart = inputField.selectionStart ?? 0
        const selectionEnd = inputField.selectionEnd ?? selectionStart
        const currentValue = inputField.value ?? ""
        const newValue = currentValue.substring(0, selectionStart) + char + currentValue.substring(selectionEnd)
        inputField.value = newValue
        form.setFieldValue(id, newValue)
        const cursorPos = selectionStart + char.length
        inputField.setSelectionRange(cursorPos, cursorPos)
        inputField.focus()
    }

    return <span className={props.inline ? "inputAssist" : ""}>
        <span className="iconWithMarginRight"><FontAwesomeIcon icon={faKeyboard} onClick={() => setInputAssistVisible(!inputAssistVisible)}/></span>
        {inputAssistVisible && collectInputAssists(globalState.inputAssists).map(assist =>
            <button type="button" className="inlineButton inputAssistButton"
                    key={assist}
                    onClick={() => handleInputAssist(props.id, assist) }
            >{assist}</button>
        )}
    </span>
}
