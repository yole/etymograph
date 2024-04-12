import {useState} from "react";
import {useForm} from "react-hook-form";
import {addPublication, updatePublication} from "@/api";
import FormRow from "@/components/FormRow";

export default function PublicationForm(props) {
    const {register, handleSubmit} = useForm({defaultValues: props.defaultValues});
    const [errorText, setErrorText] = useState("")

    function savePublication(data) {
        if (props.updateId !== undefined) {
            updatePublication(props.updateId, data).then(handleResponse)
        }
        else {
            addPublication(data).then(handleResponse)
        }
    }

    function handleResponse(r) {
        if (r.status === 200)
            r.json().then(r => props.submitted(r.id))
        else {
            r.json().then(r => setErrorText(r.message.length > 0 ? r.message : "Failed to save publication"))
        }
    }

    return <form onSubmit={handleSubmit(savePublication)}>
        <table><tbody>
        <FormRow label="Name" id="name" register={register}/>
        <FormRow label="Reference ID" id="refId" register={register}/>
        </tbody></table>
        <input type="submit" value="Save" />
        <br/>
        {errorText !== "" && <div className="errorText">{errorText}</div>}
    </form>
}
