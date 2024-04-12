import {useForm} from "react-hook-form";
import {createContext, useState} from "react";
import {useRouter} from "next/router";

export const FormRegisterContext = createContext(undefined)

export default function EtymographForm(props) {
    const {register, handleSubmit} = useForm({defaultValues: props.defaultValues});
    const [errorText, setErrorText] = useState("")
    const router = useRouter()

    function saveForm(data) {
        if (props.updateId !== undefined) {
            props.update(data).then(handleResponse)
        }
        else {
            props.create(data).then(handleResponse)
        }
    }

    function handleResponse(r) {
        if (r.status === 200) {
            if (props.submitted !== undefined || props.redirectOnCreate !== undefined) {
                r.json().then(r => {
                    if (props.redirectOnCreate !== undefined) {
                        const url = props.redirectOnCreate(r)
                        router.push(url)
                    } else {
                        props.submitted(r)
                    }
                })
            }
        }
        else {
            r.json().then(r => setErrorText(r.message.length > 0 ? r.message : "Failed to save form"))
        }
    }

    return <FormRegisterContext.Provider value={register}>
        <form onSubmit={handleSubmit(saveForm)}>
            {props.children}
            <p>
                <input type="submit" value="Save"/>
                {props.cancelled !== undefined && <>{' '}<button onClick={props.cancelled}>Cancel</button></>}
            </p>
            {errorText !== "" && <div className="errorText">{errorText}</div>}
        </form>
    </FormRegisterContext.Provider>
}
