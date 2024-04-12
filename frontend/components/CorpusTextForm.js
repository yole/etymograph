import {addCorpusText, updateCorpusText} from "@/api";
import {useForm} from "react-hook-form";
import FormRow from "@/components/FormRow";

export default function CorpusTextForm(props) {
    const {register, handleSubmit} = useForm({defaultValues: props.defaultValues});

    function saveCorpusText(data) {
        if (props.updateId !== undefined) {
            updateCorpusText(props.updateId, data)
                .then(props.submitted())
        }
        else {
            addCorpusText(props.lang, data)
                .then(r => r.json())
                .then(r => props.submitted(r))
        }
    }

    return <form onSubmit={handleSubmit(saveCorpusText)}>
        <table><tbody>
            <FormRow id="title" label="Title" register={register}/>
        </tbody></table>
        <textarea rows="10" cols="50" {...register("text")}/>
        <table><tbody>
            <FormRow id="source" label="Source" register={register}/>
        </tbody></table>
        <h3>Notes</h3>
        <textarea rows="5" cols="50" {...register("notes")}/>
        <br/>
        <input type="submit" value="Save" />
    </form>
}
