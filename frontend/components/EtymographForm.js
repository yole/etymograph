import {FormProvider, useForm} from "react-hook-form";
import {createContext, useState} from "react";
import {useRouter} from "next/router";

export const GlobalStateContext = createContext({})

export default function EtymographForm(props) {
    const methods = useForm({defaultValues: props.defaultValues});
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
            if (r.headers.get("content-type") === "application/json") {
                r.json().then(r => {
                    if (props.redirectOnCreate !== undefined) {
                        const url = props.redirectOnCreate(r)
                        router.push(url)
                    } else {
                        props.submitted(r)
                    }
                })
            }
            else if (props.submitted !== undefined) {
                props.submitted()
            }
        }
        else {
            r.json().then(r => setErrorText(r.message.length > 0 ? r.message : "Failed to save form"))
        }
    }

    return <FormProvider {...methods}>
        <GlobalStateContext.Provider value={props.globalState}>
            <form onSubmit={methods.handleSubmit(saveForm)}>
                {props.children}
                <p>
                    <input type="submit" value="Save"/>
                    {props.cancelled !== undefined && <>{' '}
                        <button onClick={props.cancelled}>Cancel</button>
                    </>}
                </p>
                {errorText !== "" && <div className="errorText">{errorText}</div>}
            </form>
        </GlobalStateContext.Provider>
    </FormProvider>
}
