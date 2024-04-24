import {FormProvider, useForm} from "react-hook-form";
import {useContext, useState} from "react";
import {useRouter} from "next/router";
import {EditModeContext, SetEditModeContext} from "@/components/EtymographFormView";

export default function EtymographForm(props) {
    const methods = useForm({defaultValues: props.defaultValues});
    const [errorText, setErrorText] = useState("")
    const router = useRouter()
    const editMode = useContext(EditModeContext)
    const setEditMode = props.setEditMode ?? useContext(SetEditModeContext)

    async function saveForm(data) {
        const r = props.updateId !== undefined ? await props.update(data) : await props.create(data)
        if (r.status === 200) {
            if (r.headers.get("content-type") === "application/json") {
                const jr = await r.json()
                if (props.redirectOnCreate !== undefined) {
                    const url = props.redirectOnCreate(jr)
                    router.push(url)
                }
                else if (props.submitted !== undefined) {
                    const result = props.submitted(jr, data)
                    if (result !== undefined && result.message !== undefined) {
                        setErrorText(result.message)
                    }
                }
                else if (setEditMode !== undefined) {
                    router.replace(router.asPath)
                    setEditMode(false)
                }
            }
            else if (props.submitted !== undefined) {
                props.submitted()
            }
            else if (setEditMode !== undefined) {
                router.replace(router.asPath)
                setEditMode(false)
            }
        }
        else {
            const jr = await r.json()
            setErrorText(jr.message.length > 0 ? jr.message : "Failed to save form")
        }
    }

    if (editMode === false) return <></>

    return <FormProvider {...methods}>
        <form onSubmit={methods.handleSubmit(saveForm)}>
            {props.children}
            <p>
                <input type="submit" value="Save"/>
                {(props.cancelled !== undefined || setEditMode !== undefined) && <>{' '}
                    <button onClick={() => props.cancelled !== undefined ? props.cancelled() : setEditMode(false)}>Cancel</button>
                </>}
            </p>
            {errorText !== "" && <div className="errorText">{errorText}</div>}
        </form>
    </FormProvider>
}
