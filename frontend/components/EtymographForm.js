import {FormProvider, useForm} from "react-hook-form";
import {createContext, useState} from "react";
import {useRouter} from "next/router";

export const GlobalStateContext = createContext({})

export default function EtymographForm(props) {
    const methods = useForm({defaultValues: props.defaultValues});
    const [errorText, setErrorText] = useState("")
    const router = useRouter()

    async function saveForm(data) {
        const r = props.updateId !== undefined ? await props.update(data) : await props.create(data)
        if (r.status === 200) {
            if (r.headers.get("content-type") === "application/json") {
                const jr = await r.json()
                if (props.redirectOnCreate !== undefined) {
                    const url = props.redirectOnCreate(jr)
                    router.push(url)
                } else {
                    const result = props.submitted(jr, data)
                    if (result !== undefined && result.message !== undefined) {
                        setErrorText(result.message)
                    }
                }
            }
            else if (props.submitted !== undefined) {
                props.submitted()
            }
        }
        else {
            const jr = await r.json()
            setErrorText(jr.message.length > 0 ? jr.message : "Failed to save form")
        }
    }

    let form = <form onSubmit={methods.handleSubmit(saveForm)}>
        {props.children}
        <p>
            <input type="submit" value="Save"/>
            {props.cancelled !== undefined && <>{' '}
                <button onClick={props.cancelled}>Cancel</button>
            </>}
        </p>
        {errorText !== "" && <div className="errorText">{errorText}</div>}
    </form>

    if (props.globalState !== undefined) {
        return <FormProvider {...methods}>
            <GlobalStateContext.Provider value={props.globalState}>
                {form}
            </GlobalStateContext.Provider>
        </FormProvider>
    }
    return <FormProvider {...methods}>
        {form}
    </FormProvider>
}
