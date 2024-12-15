import {FormProvider, useForm} from "react-hook-form";
import {useContext, useEffect, useState} from "react";
import {useRouter} from "next/router";
import {EditModeContext, SetEditModeContext} from "@/components/EtymographFormView";

interface EtymographFormProps<Data> {
    defaultValues?: Data;
    setEditMode?: (newEditMode: boolean) => void;
    focusTarget?: any;
    updateId?: number;
    update?: (Data: Data) => Promise<Response>;
    create?: (Data: Data) => Promise<Response>;
    children?: React.ReactNode;
    submitted?: (response: Response, data: any) => any;
    setFocusTarget?: (newFocusTarget: any) => void;
    redirectOnCreate?: any;
    buttons?: any;
    cancelled?: any;
}

export default function EtymographForm<Data>(props: EtymographFormProps<Data>) {
    const methods = useForm({defaultValues: props.defaultValues as any});
    const [errorText, setErrorText] = useState("")
    const router = useRouter()
    const editMode = useContext(EditModeContext)
    const setEditModeContext = useContext(SetEditModeContext)
    const setEditMode = props.setEditMode ?? setEditModeContext

    useEffect(() => { if (props.focusTarget) methods.setFocus(props.focusTarget) }, []);

    if (props.focusTarget) {
        props.setFocusTarget(null)
    }

    async function saveForm(data: Data) {
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
                    if (result !== undefined) {
                        if (result.message !== undefined) {
                            setErrorText(result.message)
                        }
                        else if (result.then !== undefined) {
                            result.then(r => {
                                if (r !== undefined && r.message !== undefined) setErrorText(r.message)
                            })
                        }
                    }
                }
                else if (setEditMode !== undefined) {
                    router.replace(router.asPath)
                    setEditMode(false)
                }
            }
            else if (props.submitted !== undefined) {
                props.submitted(undefined, undefined)
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
    const buttons = props.buttons || []

    return <FormProvider {...methods}>
        <form onSubmit={methods.handleSubmit(saveForm)}>
            {props.children}
            <p>
                <input type="submit" value="Save"/>
                {(props.cancelled !== undefined || setEditMode !== undefined) && <>{' '}
                    <button onClick={() => props.cancelled !== undefined ? props.cancelled() : setEditMode(false)}>Cancel</button>
                </>}
                {buttons.map(b => <>{' '}<button type="button" onClick={() => b.callback(methods.getValues())}>{b.text}</button></>)}
            </p>
            {errorText !== "" && <div className="errorText">{errorText}</div>}
        </form>
    </FormProvider>
}
