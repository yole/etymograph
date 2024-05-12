import {useFormContext} from "react-hook-form";
import {useContext} from "react";
import {GlobalStateContext} from "@/components/Contexts";

export default function FormRow(props) {
    const {register, getValues, setValue} = useFormContext()
    const globalState = useContext(GlobalStateContext)

    function collectInputAssists(assists) {
        return assists.graphemes.map(g => g.text)
    }

    function handleInputAssist(id, char) {
        const inputField = document.getElementById(id)
        const currentValue = inputField.value
        const newValue = currentValue.substring(0, inputField.selectionStart) + char + currentValue.substring(inputField.selectionEnd)
        setValue(id, newValue)
    }

    return <tr onBlur={() => {
        if (props.handleBlur !== undefined) props.handleBlur(getValues())
        }}>
        <td><label htmlFor={props.id}>{props.label}:</label></td>
        <td>
            <input id={props.id} readOnly={props.readOnly} size={props.size} type="text" {...register(props.id)}/>
            {props.inputAssist && globalState.inputAssists && <span className="inputAssist">
                {collectInputAssists(globalState.inputAssists).map(assist =>
                    <button className="inlineButton inputAssistButton"
                            onClick={(e) => {
                                e.preventDefault()
                                handleInputAssist(props.id, assist)
                            }}
                    >{assist}</button>
                )}
            </span>}
            {props.children}
        </td>
    </tr>
}
