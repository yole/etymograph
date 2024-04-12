import {useForm} from "react-hook-form";
import {createContext, useState} from "react";

export const FormRegisterContext = createContext(undefined)

export default function EtymographForm(props) {
    const {register, handleSubmit} = useForm({defaultValues: props.defaultValues});
    const [errorText, setErrorText] = useState("")

    function saveForm(data) {
        if (props.updateId !== undefined) {
            props.update(data).then(handleResponse)
        }
        else {
            props.create(data).then(handleResponse)
        }
    }

    function handleResponse(r) {
        if (r.status === 200)
            r.json().then(r => props.submitted(r))
        else {
            r.json().then(r => setErrorText(r.message.length > 0 ? r.message : "Failed to save form"))
        }
    }

    return <FormRegisterContext.Provider value={register}>
        <form onSubmit={handleSubmit(saveForm)}>
            {props.children}
            <p>
                <input type="submit" value="Save"/>
            </p>
            {errorText !== "" && <div className="errorText">{errorText}</div>}
        </form>
    </FormRegisterContext.Provider>
}
