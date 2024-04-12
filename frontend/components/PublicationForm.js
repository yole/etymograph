import {useState} from "react";
import {useForm} from "react-hook-form";
import {addPublication, updatePublication} from "@/api";

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
        <tr>
            <td><label>Name:</label></td>
            <td><input type="text" {...register("name")}/></td>
        </tr>
        <tr>
            <td><label>Reference ID: </label></td>
            <td><input type="text" {...register("refId")}/></td>
        </tr>
        </tbody></table>
        <input type="submit" value="Save" />
        <br/>
        {errorText !== "" && <div className="errorText">{errorText}</div>}
    </form>
}
