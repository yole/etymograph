import {useFormContext} from "react-hook-form";
import InputAssist from "@/components/InputAssist";

export default function FormRow(props) {
    const {register, getValues} = useFormContext()

    return <tr onBlur={() => {
        if (props.handleBlur !== undefined) props.handleBlur(getValues())
    }}>
        <td><label htmlFor={props.id}>{props.label}:</label></td>
        <td>
            <input id={props.id} readOnly={props.readOnly} size={props.size} type="text" {...register(props.id)}/>
            {props.inputAssist && <InputAssist id={props.id} inline={true}/>}
            {props.children}
        </td>
    </tr>
}
